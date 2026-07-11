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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
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
  private final Executor validationExecutor;
  private final Semaphore capacity;

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
      @Qualifier("validationExecutor") Executor validationExecutor,
      @Value("${app.async.workers}") int workers) {
    this.repo = repo;
    this.storage = storage;
    this.profileService = profileService;
    this.overrideApplier = overrideApplier;
    this.validator = validator;
    this.om = om;
    this.circuitRegistry = circuitRegistry;
    this.maxPickupAttempts = maxPickupAttempts;
    this.staleRunningTimeout = staleRunningTimeout;
    this.validationExecutor = validationExecutor;
    this.capacity = new Semaphore(workers);
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
              try {
                process(id);
              } catch (Exception e) {
                log.error("worker error on job {}", id, e);
              } finally {
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
      byte[] file = storage.read(job.getDocumentPath());
      var profile = profileService.getOrDefault(job.getProfileId());
      String policy = profile.getPolicyXml();
      if (job.getProfileOverrides() != null && !job.getProfileOverrides().isBlank()) {
        Map<String, Object> ov = om.readValue(job.getProfileOverrides(), new TypeReference<>() {});
        policy = overrideApplier.apply(policy, ov);
      }
      Set<ReportType> reports = parseReports(job.getReportsRequested());
      var result =
          validator.validate(
              new ValidationRequest(file, job.getDocumentFilename(), policy, reports));

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
      job.setResultPath(resultPath);
      job.setStatus(JobStatus.COMPLETED);
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    } catch (AppException ae) {
      if (Errors.DSS_UNAVAILABLE.equals(ae.getCode())
          && job.getPickupAttempts() < maxPickupAttempts) {
        // Reset the in-progress marker set by the claim so the requeued job is not mistaken for a
        // running one by startedAt-based "in progress" queries / SLA metrics.
        job.setStartedAt(null);
        job.setStatus(JobStatus.PENDING);
        // Give back the claim's increment: transient DSS unavailability must not consume the
        // poison-job pickup budget. Retries stay bounded by the job TTL + the DSS circuit breaker.
        job.setPickupAttempts(job.getPickupAttempts() - 1);
        repo.save(job);
        return;
      }
      job.setStatus(JobStatus.FAILED);
      job.setErrorCode(ae.getCode());
      job.setErrorMessage(ae.getDetail());
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    } catch (Exception e) {
      // Never persist raw exception text: it reaches the job-status response and the callback
      // payload. Clients get the catalog code; the specifics stay in the log.
      log.error("job {} failed with unexpected error", id, e);
      job.setStatus(JobStatus.FAILED);
      job.setErrorCode(Errors.INTERNAL);
      job.setErrorMessage("unexpected error");
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
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
