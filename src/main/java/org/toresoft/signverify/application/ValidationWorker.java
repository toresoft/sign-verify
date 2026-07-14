/**
 * sign-verify Copyright (C) 2026 toresoft
 *
 * <p>This file is part of the "sign-verify" project.
 *
 * <p>This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301 USA
 */
package org.toresoft.signverify.application;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.*;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ValidationWorker {

  private static final Logger log = LoggerFactory.getLogger(ValidationWorker.class);

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final VerificationProfileService profileService;
  private final PolicyOverrideApplier overrideApplier;
  private final SignatureValidatorPort validator;
  private final JsonMapper om;
  private final CircuitBreakerRegistry circuitRegistry;
  private final int maxPickupAttempts;
  private final Duration staleRunningTimeout;
  private final Duration maxValidationTime;
  private final Executor validationExecutor;
  private final Semaphore capacity;
  private final MeterRegistry meterRegistry;
  // Per-instance deadlines for jobs THIS instance is validating. A DB sweep cannot enforce the
  // budget safely in a multi-instance deployment (it cannot tell "slow but alive on instance B"
  // from "crashed on instance B"); each instance times out only its own in-flight jobs. Crash of
  // this instance drops these deadlines, and the (longer) stale-RUNNING sweep requeues the job.
  private final ConcurrentHashMap<UUID, Instant> deadlines = new ConcurrentHashMap<>();

  public ValidationWorker(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      VerificationProfileService profileService,
      PolicyOverrideApplier overrideApplier,
      SignatureValidatorPort validator,
      JsonMapper om,
      CircuitBreakerRegistry circuitRegistry,
      @Value("${app.async.max-pickup-attempts}") int maxPickupAttempts,
      @Value("${app.async.worker.stale-running-timeout}") Duration staleRunningTimeout,
      @Value("${app.async.worker.max-validation-time}") Duration maxValidationTime,
      @Qualifier("validationExecutor") Executor validationExecutor,
      @Value("${app.async.workers}") int workers,
      MeterRegistry meterRegistry) {
    // The watchdog must fire before the stale-RUNNING sweep, or a slow-but-alive job would be
    // requeued (crash-recovery path) instead of failing deterministically with job.timeout.
    if (maxValidationTime.compareTo(staleRunningTimeout) >= 0) {
      throw new IllegalStateException(
          "app.async.worker.max-validation-time ("
              + maxValidationTime
              + ") must be < stale-running-timeout ("
              + staleRunningTimeout
              + ")");
    }
    this.repo = repo;
    this.storage = storage;
    this.profileService = profileService;
    this.overrideApplier = overrideApplier;
    this.validator = validator;
    this.om = om;
    this.circuitRegistry = circuitRegistry;
    this.maxPickupAttempts = maxPickupAttempts;
    this.staleRunningTimeout = staleRunningTimeout;
    this.maxValidationTime = maxValidationTime;
    this.validationExecutor = validationExecutor;
    this.capacity = new Semaphore(workers);
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(fixedDelayString = "${app.async.worker.poll-interval}")
  public void poll() {
    CircuitBreaker dssCircuit = circuitRegistry.circuitBreaker("dssValidator");
    if (dssCircuit.getState() == CircuitBreaker.State.OPEN) return;
    // Recover jobs stranded in RUNNING by a crashed instance: without this sweep they would sit
    // invisible to the picker (which only selects PENDING) until their expiry, days later.
    int requeued = repo.resetStaleRunning(Instant.now().minus(staleRunningTimeout));
    if (requeued > 0) log.warn("requeued {} stale RUNNING job(s)", requeued);

    // Backpressure: only claim as many jobs as there is free worker capacity, so poll() never
    // claims a job it cannot immediately start (which would strand it in RUNNING until the sweep).
    int free = capacity.availablePermits();
    if (free == 0) return;
    var jobs = repo.findPickablePending(maxPickupAttempts, Instant.now(), PageRequest.of(0, free));
    for (ValidationJob j : jobs) {
      if (!capacity.tryAcquire()) break;
      UUID id = j.getId();
      try {
        // Offload the (potentially minutes-long) DSS validation to the dedicated executor so the
        // shared @Scheduled thread returns immediately and other schedulers are not starved.
        validationExecutor.execute(
            () -> {
              deadlines.put(id, Instant.now().plus(maxValidationTime));
              try {
                process(id);
              } catch (Exception e) {
                log.error("worker error on job {}", id, e);
              } finally {
                deadlines.remove(id);
                capacity.release();
              }
            });
      } catch (RejectedExecutionException rex) {
        capacity.release();
        log.warn("validation executor saturated; deferring job {} to next poll", id);
        break;
      }
    }
  }

  /**
   * Times out jobs THIS instance has been validating past their budget. Runs on a short fixed
   * delay. Each over-deadline job is failed with job.timeout via a conditional bulk update (no-op
   * if it already finished) and then forgotten so it is not timed out twice. The orphaned
   * validation thread keeps draining (bounded by the DSS HTTP timeouts) and its eventual
   * markCompleted/ markFailed no-ops because the job is no longer RUNNING.
   */
  @Scheduled(fixedDelayString = "${app.async.worker.budget-check-interval}")
  public void enforceBudgets() {
    Instant now = Instant.now();
    for (Map.Entry<UUID, Instant> e : deadlines.entrySet()) {
      if (now.isAfter(e.getValue())) {
        UUID id = e.getKey();
        // Remove the deadline ONLY after confirming the DB outcome. If markTimedOut throws
        // (transient
        // DB blip / lock), the entry stays so the next tick retries the timeout — otherwise the job
        // would hang until the 30m stale-RUNNING sweep (requeue, not the deterministic
        // job.timeout).
        // We also remove on 0 (job already finalized by another path) so a dead deadline is
        // dropped.
        try {
          if (repo.markTimedOut(id, now) == 1) {
            deadlines.remove(id);
            meterRegistry.counter("signverify.validation.timeout").increment();
            log.warn("job {} timed out: exceeded validation budget of {}", id, maxValidationTime);
          } else {
            deadlines.remove(id);
          }
        } catch (RuntimeException ex) {
          log.warn("markTimedOut for job {} failed; deadline kept for next tick", id, ex);
        }
      }
    }
  }

  // Test seam: register an in-flight deadline without going through the executor. Package-private
  // on purpose; not part of the production API.
  void registerDeadlineForTest(UUID id, Instant deadline) {
    deadlines.put(id, deadline);
  }

  // Deliberately NOT @Transactional (and it would be dead anyway: poll() self-invokes, bypassing
  // the Spring proxy). The atomic claim commits in its own transaction (see claimForProcessing),
  // and the outcome writes below auto-commit individually; wrapping the whole method would hold a
  // DB transaction open across a potentially minutes-long DSS validation. Crash recovery between
  // claim and outcome is handled by the stale-RUNNING sweep in poll().
  public void process(UUID id) {
    // Atomic claim: only the worker whose conditional update affects a row proceeds. In a
    // multi-instance deployment this guarantees a single owner per job, avoiding duplicate
    // validation and duplicate callbacks. The status flip and attempt increment happen in the DB.
    if (repo.claimForProcessing(id, Instant.now()) == 0) return;
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null) return;

    try {
      long jobStart = System.nanoTime();
      long t0 = System.nanoTime();
      enterPhase(id, ValidationPhase.STORAGE_READ, jobStart);
      byte[] file = storage.read(job.getDocumentPath());
      recordPhase(ValidationPhase.STORAGE_READ, t0);

      long t1 = System.nanoTime();
      enterPhase(id, ValidationPhase.POLICY_RESOLVE, jobStart);
      var profile = profileService.getOrDefault(job.getProfileId());
      String policy = profile.getPolicyXml();
      if (job.getProfileOverrides() != null && !job.getProfileOverrides().isBlank()) {
        Map<String, Object> ov = om.readValue(job.getProfileOverrides(), new TypeReference<>() {});
        policy = overrideApplier.apply(policy, ov);
      }
      Set<ReportType> reports = parseReports(job.getReportsRequested());
      recordPhase(ValidationPhase.POLICY_RESOLVE, t1);
      // Capture the post-override policy into an effectively-final binding for the lambda below.
      final String finalPolicy = policy;
      final byte[] finalFile = file;
      final Set<ReportType> finalReports = reports;

      long t2 = System.nanoTime();
      enterPhase(id, ValidationPhase.DSS_VALIDATE, jobStart);
      log.info("job {} entering DSS validation", id);
      var result =
          runTimed(
              ValidationPhase.DSS_VALIDATE,
              t2,
              () ->
                  validator.validate(
                      new ValidationRequest(
                          finalFile, job.getDocumentFilename(), finalPolicy, finalReports)));

      long t3 = System.nanoTime();
      enterPhase(id, ValidationPhase.RESULT_STORE, jobStart);
      Map<String, Object> resultJson = new LinkedHashMap<>();
      resultJson.put("indication", result.indication());
      resultJson.put("subIndication", result.subIndication());
      resultJson.put("signatureFormat", result.signatureFormat());
      resultJson.put("signatureCount", result.signatureCount());
      resultJson.put("profileUsed", profile.getName());
      Map<String, Object> rep = new LinkedHashMap<>();
      for (var e : result.reportsJson().entrySet()) {
        rep.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
      }
      resultJson.put("reports", rep);
      String resultPath =
          storage.storeResult(job.getId().toString(), om.writeValueAsBytes(resultJson));
      recordPhase(ValidationPhase.RESULT_STORE, t3);

      // Finalize via a conditional bulk UPDATE (status=RUNNING), not save() on the stale entity:
      // polling GETs and the cleanup scheduler mutate this row concurrently, and save() would throw
      // ObjectOptimisticLockingFailureException on a stale @Version — losing the result. If the job
      // is no longer RUNNING (requeued by the stale sweep, or expired), this is a no-op and the
      // outcome is intentionally dropped.
      int updated = repo.markCompleted(id, resultPath, Instant.now());
      if (updated == 0) {
        // The job is no longer RUNNING (timed out by the watchdog, requeued by the stale sweep, or
        // expired) while the validation drained. The result file was just written to disk; the DB
        // row has result_path=null (this update was a no-op), so the cleanup scheduler's
        // delete(resultPath) would receive null and leak the file forever. Delete it here.
        storage.delete(resultPath);
        log.info("job {} result dropped and file cleaned: no longer RUNNING at finalize", id);
      } else {
        log.info("job {} completed", id);
      }
    } catch (AppException ae) {
      if (Errors.DSS_UNAVAILABLE.equals(ae.getCode())
          && job.getPickupAttempts() < maxPickupAttempts) {
        // Give back the claim's pickup-attempt increment via the conditional requeue: transient DSS
        // unavailability must not consume the poison-job budget. Retries stay bounded by the job
        // TTL + the DSS circuit breaker. No-op (logged) if the job is no longer RUNNING.
        int updated = repo.requeueTransient(id);
        if (updated == 0) {
          log.info("job {} not requeued: no longer RUNNING after transient DSS error", id);
        }
        return;
      }
      markFailedConditional(id, ae.getCode(), ae.getDetail());
    } catch (Exception e) {
      // Never persist raw exception text: it reaches the job-status response and the callback
      // payload. Clients get the catalog code; the specifics stay in the log.
      log.error("job {} failed with unexpected error", id, e);
      markFailedConditional(id, Errors.INTERNAL, "unexpected error");
    }
  }

  // Finalize-as-FAILED via the conditional bulk UPDATE. Extracted so both catch arms share the
  // identical no-op-on-not-RUNNING semantics (and the same log line).
  private void markFailedConditional(UUID id, String code, String detail) {
    int updated = repo.markFailed(id, code, detail, Instant.now());
    if (updated == 0) {
      log.info("job {} failure not recorded: no longer RUNNING at finalize", id);
    }
  }

  // Phase stamping is gated by a wall-clock threshold: fast jobs (sub-second) do not pay the 4
  // extra round-trips, since the phase column only matters for diagnosing slow/stuck jobs. The
  // threshold is measured from job claim, not per-phase.
  private volatile long phaseStampThresholdNanos = TimeUnit.SECONDS.toNanos(1);

  // Stamps the current validation phase via the version-neutral conditional update so a stuck job
  // is diagnosable through GET /jobs/{id} (and directly in the DB) without grepping logs. Skipped
  // for fast jobs (below the threshold) to avoid write amplification where it has no diagnostic
  // value.
  private void enterPhase(UUID id, ValidationPhase phase, long jobStartNanos) {
    if (System.nanoTime() - jobStartNanos < phaseStampThresholdNanos) return;
    repo.setPhase(id, phase, Instant.now());
  }

  // Test seam: see enterPhase. Package-private on purpose; not part of the production API.
  void setPhaseStampThresholdForTest(long nanos) {
    this.phaseStampThresholdNanos = nanos;
  }

  private void recordPhase(ValidationPhase phase, long startNanos) {
    meterRegistry
        .timer("signverify.validation.phase", "phase", phase.name())
        .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
  }

  // Runs a phase's work and records its duration even when it throws, so the DSS_VALIDATE timer
  // captures slow/failed/timeout validations (not just the successful ones) — otherwise p95/p99
  // would be systematically understated and hide exactly the slow cases the metric exists to find.
  private <T> T runTimed(
      ValidationPhase phase, long startNanos, java.util.function.Supplier<T> work) {
    try {
      return work.get();
    } finally {
      recordPhase(phase, startNanos);
    }
  }

  private Set<ReportType> parseReports(String csv) {
    Set<ReportType> set = EnumSet.noneOf(ReportType.class);
    for (String s : csv.split(",")) {
      try {
        set.add(ReportType.valueOf(s.trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (set.isEmpty()) set.add(ReportType.SIMPLE);
    return set;
  }
}
