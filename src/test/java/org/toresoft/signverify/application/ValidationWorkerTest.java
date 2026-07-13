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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link ValidationWorker}.
 *
 * <p>All tests are pure Mockito unit tests; no Spring context is started. These tests are expected
 * to FAIL until the {@code ValidationWorker} class is implemented (FASE 9, Task 9.6).
 */
@ExtendWith(MockitoExtension.class)
class ValidationWorkerTest {

  private static final int MAX_PICKUP_ATTEMPTS = 3;
  private static final UUID PROFILE_ID = UUID.randomUUID();

  @Mock private ValidationJobRepository repo;
  @Mock private DocumentStoragePort storage;
  @Mock private VerificationProfileService profileService;
  @Mock private PolicyOverrideApplier applier;
  @Mock private SignatureValidatorPort validator;
  @Mock private CircuitBreakerRegistry registry;
  @Mock private CircuitBreaker circuit;

  private final JsonMapper om = new JsonMapper();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private ValidationWorker worker;

  private static final int WORKERS = 4;

  private ValidationWorker workerWithBudget(Duration maxValidationTime, Duration staleRunning) {
    // Executor that runs submitted tasks inline so process() completes within the test.
    Executor inline = Runnable::run;
    return new ValidationWorker(
        repo,
        storage,
        profileService,
        applier,
        validator,
        om,
        registry,
        MAX_PICKUP_ATTEMPTS,
        staleRunning,
        maxValidationTime,
        inline,
        WORKERS,
        meterRegistry);
  }

  @BeforeEach
  void setUp() {
    worker =
        new ValidationWorker(
            repo,
            storage,
            profileService,
            applier,
            validator,
            om,
            registry,
            MAX_PICKUP_ATTEMPTS,
            java.time.Duration.ofMinutes(30),
            java.time.Duration.ofMinutes(5),
            // Inline executor: run submitted work synchronously so poll() behaves like the pre-
            // offload version under test (assertions can observe process() side effects at once).
            Runnable::run,
            WORKERS,
            meterRegistry);
    lenient().when(registry.circuitBreaker("dssValidator")).thenReturn(circuit);
    // By default the worker wins the atomic claim; tests that exercise the lost-race / not-pending
    // path override this with 0.
    lenient().when(repo.claimForProcessing(any(), any())).thenReturn(1);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private ValidationJob newPendingJob() {
    return newPendingJob(null, null);
  }

  private ValidationJob newPendingJob(String callbackUrl, String overridesJson) {
    ValidationJob job = new ValidationJob();
    job.setId(UUID.randomUUID());
    job.setStatus(JobStatus.PENDING);
    job.setProfileId(PROFILE_ID);
    job.setProfileOverrides(overridesJson);
    job.setReportsRequested("simple,etsi");
    job.setDocumentPath("storage/abc");
    job.setDocumentFilename("doc.pdf");
    job.setCallbackUrl(callbackUrl);
    job.setPickupAttempts(0);
    return job;
  }

  private VerificationProfile profile() {
    VerificationProfile p = new VerificationProfile();
    p.setId(PROFILE_ID);
    p.setName("default");
    p.setPolicyXml("<Constraints/>");
    return p;
  }

  private ValidationResult validationResult() {
    return new ValidationResult(
        "PAdES",
        "TOTAL_PASSED",
        null,
        1,
        Map.of(ReportType.SIMPLE, "{\"ok\":true}", ReportType.ETSI, "{\"etsi\":true}"),
        List.of(),
        List.of());
  }

  /**
   * Stubs the typical happy-path collaborator chain: storage.read, profile lookup, validator, and
   * the finalize bulk update (returns 1 = finalized).
   *
   * @return the returned {@link ValidationResult} for further assertions
   */
  private ValidationResult stubHappyPath() {
    when(storage.read(anyString())).thenReturn(new byte[] {1, 2, 3});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    ValidationResult result = validationResult();
    when(validator.validate(any(ValidationRequest.class))).thenReturn(result);
    when(storage.storeResult(anyString(), any())).thenReturn("storage/result/path");
    // markCompleted returns 1 by default (the finalize landed); tests that exercise the no-op path
    // (job no longer RUNNING) override this with 0.
    lenient().when(repo.markCompleted(any(), anyString(), any())).thenReturn(1);
    return result;
  }

  // ---------------------------------------------------------------------
  // budget watchdog tests
  // ---------------------------------------------------------------------

  @Test
  void constructor_rejectsBudgetNotSmallerThanStaleTimeout() {
    assertThatThrownBy(() -> workerWithBudget(Duration.ofMinutes(30), Duration.ofMinutes(30)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void enforceBudgets_timesOutPastDeadlineJobs_incrementsCounter_andForgetsThem() {
    ValidationWorker w = workerWithBudget(Duration.ofMinutes(5), Duration.ofMinutes(30));
    UUID overdue = UUID.randomUUID();
    // Register a deadline already in the past for this instance's job.
    w.registerDeadlineForTest(overdue, Instant.now().minusSeconds(1));
    when(repo.markTimedOut(eq(overdue), any())).thenReturn(1);

    w.enforceBudgets();

    verify(repo).markTimedOut(eq(overdue), any());
    assertThat(meterRegistry.counter("signverify.validation.timeout").count()).isEqualTo(1.0);
    // Second run must not re-time-out the same job (entry was forgotten).
    w.enforceBudgets();
    verify(repo, times(1)).markTimedOut(eq(overdue), any());
  }

  @Test
  void enforceBudgets_ignoresJobsWithinDeadline() {
    ValidationWorker w = workerWithBudget(Duration.ofMinutes(5), Duration.ofMinutes(30));
    w.registerDeadlineForTest(UUID.randomUUID(), Instant.now().plusSeconds(300));

    w.enforceBudgets();

    verify(repo, never()).markTimedOut(any(), any());
  }

  // ---------------------------------------------------------------------
  // process(...) tests
  // ---------------------------------------------------------------------

  @Test
  void process_picksUpPendingJob_andCompletes() {
    ValidationJob job = newPendingJob();
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    ValidationResult result = stubHappyPath();

    worker.process(job.getId());

    // Finalize goes through the conditional bulk UPDATE (status=RUNNING), not save() on the entity.
    verify(repo).markCompleted(eq(job.getId()), eq("storage/result/path"), any());
    verify(repo, never()).save(any());

    verify(validator, times(1)).validate(any(ValidationRequest.class));
    verify(storage).read(job.getDocumentPath());
    verify(storage).storeResult(eq(job.getId().toString()), any());
    assertThat(result.indication()).isEqualTo("TOTAL_PASSED");
  }

  @Test
  void process_nonExistentJob_isNoOp() {
    UUID id = UUID.randomUUID();
    when(repo.claimForProcessing(eq(id), any())).thenReturn(1);
    when(repo.findById(id)).thenReturn(Optional.empty());

    worker.process(id);

    verify(repo, never()).save(any());
    verify(repo, never()).markCompleted(any(), anyString(), any());
    verify(repo, never()).markFailed(any(), anyString(), anyString(), any());
    verify(repo, never()).requeueTransient(any());
    verify(validator, never()).validate(any());
  }

  @Test
  void process_lostClaim_isNoOp() {
    UUID id = UUID.randomUUID();
    // Another instance already claimed the job (PENDING -> RUNNING): claim affects 0 rows.
    when(repo.claimForProcessing(eq(id), any())).thenReturn(0);

    worker.process(id);

    verify(repo, never()).findById(any());
    verify(repo, never()).save(any());
    verify(repo, never()).markCompleted(any(), anyString(), any());
    verify(validator, never()).validate(any());
  }

  @Test
  void process_appliesProfileOverrides_whenPresent() {
    ValidationJob job = newPendingJob();
    job.setProfileOverrides("{\"checkRevocation\":false}");
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {0});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(applier.apply(eq("<Constraints/>"), anyMap())).thenReturn("<Constraints overridden/>");
    when(validator.validate(any(ValidationRequest.class))).thenReturn(validationResult());
    when(storage.storeResult(anyString(), any())).thenReturn("storage/result/path");

    worker.process(job.getId());

    verify(applier, times(1)).apply(eq("<Constraints/>"), anyMap());
  }

  @Test
  void process_skipsOverrideApplier_whenOverridesBlank() {
    ValidationJob job = newPendingJob();
    job.setProfileOverrides("   ");
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {0});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(validator.validate(any(ValidationRequest.class))).thenReturn(validationResult());
    when(storage.storeResult(anyString(), any())).thenReturn("storage/result/path");

    worker.process(job.getId());

    verify(applier, never()).apply(anyString(), anyMap());
  }

  @Test
  void process_dssUnavailable_revertsToPending() {
    ValidationJob job = newPendingJob();
    // Post-claim state (the atomic claim already incremented the counter and set startedAt); still
    // below the cap.
    job.setPickupAttempts(1);
    job.setStartedAt(java.time.Instant.now());
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {0});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(validator.validate(any(ValidationRequest.class)))
        .thenThrow(AppException.dssUnavailable("dss down"));

    worker.process(job.getId());

    // The requeue goes through the conditional bulk UPDATE (status=RUNNING); clearing startedAt and
    // decrementing pickupAttempts happens in JPQL, so the worker never touches the entity.
    verify(repo).requeueTransient(job.getId());
    verify(repo, never()).save(any());
    verify(repo, never()).markFailed(any(), anyString(), anyString(), any());
    verify(repo, never()).markCompleted(any(), anyString(), any());
  }

  @Test
  void process_dssUnavailableMaxAttempts_marksFailed() {
    ValidationJob job = newPendingJob();
    // Post-claim state: the claim brought the counter up to the cap, so no further retry.
    job.setPickupAttempts(MAX_PICKUP_ATTEMPTS);
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {0});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(validator.validate(any(ValidationRequest.class)))
        .thenThrow(AppException.dssUnavailable("dss down"));

    worker.process(job.getId());

    verify(repo).markFailed(eq(job.getId()), eq(Errors.DSS_UNAVAILABLE), anyString(), any());
    verify(repo, never()).requeueTransient(any());
    verify(repo, never()).save(any());
  }

  @Test
  void process_validatorException_marksFailed() {
    ValidationJob job = newPendingJob();
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {0});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(validator.validate(any(ValidationRequest.class))).thenThrow(new RuntimeException("boom"));

    worker.process(job.getId());

    // Raw exception text must never be persisted: it would leak internals to the client via the
    // job-status response and the callback payload.
    verify(repo).markFailed(eq(job.getId()), eq(Errors.INTERNAL), eq("unexpected error"), any());
    verify(repo, never()).save(any());
  }

  @Test
  void process_setsNextCallbackAt_whenCallbackUrlSet() {
    ValidationJob job = newPendingJob("https://callback.example/hook", null);
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    stubHappyPath();

    worker.process(job.getId());

    // The callback is armed in the markCompleted JPQL (CASE WHEN callbackUrl IS NOT NULL). The unit
    // test only asserts the worker routes through markCompleted; the JPQL arming is covered by the
    // JPA repository test.
    verify(repo).markCompleted(eq(job.getId()), anyString(), any());
    verify(repo, never()).save(any());
  }

  @Test
  void process_noCallbackUrl_doesNotSetNextCallbackAt() {
    ValidationJob job = newPendingJob(null, null);
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    stubHappyPath();

    worker.process(job.getId());

    verify(repo).markCompleted(eq(job.getId()), anyString(), any());
    verify(repo, never()).save(any());
  }

  @Test
  void process_finalizeNoLongerRunning_isLoggedNoOp() {
    // The job was requeued by the stale-RUNNING sweep (or expired) while the worker validated. The
    // conditional bulk update affects 0 rows: the outcome is intentionally dropped, no exception.
    ValidationJob job = newPendingJob();
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {1, 2, 3});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(validator.validate(any(ValidationRequest.class))).thenReturn(validationResult());
    when(storage.storeResult(anyString(), any())).thenReturn("storage/result/path");
    when(repo.markCompleted(any(), anyString(), any())).thenReturn(0);

    worker.process(job.getId());

    verify(repo).markCompleted(eq(job.getId()), anyString(), any());
    verify(repo, never()).save(any());
    verify(repo, never()).markFailed(any(), anyString(), anyString(), any());
  }

  // ---------------------------------------------------------------------
  // poll() tests
  // ---------------------------------------------------------------------

  @Test
  void poll_requeuesStaleRunningJobs_beforePicking() {
    when(circuit.getState()).thenReturn(CircuitBreaker.State.CLOSED);

    worker.poll();

    // Jobs stranded in RUNNING by a crashed instance are recovered on every poll, not only when
    // they expire days later.
    verify(repo, times(1)).resetStaleRunning(any(java.time.Instant.class));
  }

  @Test
  void poll_skipsWhenCircuitOpen() {
    when(circuit.getState()).thenReturn(CircuitBreaker.State.OPEN);

    worker.poll();

    verify(repo, never()).findPickablePending(anyInt(), any(), any());
    verify(validator, never()).validate(any());
  }

  @Test
  void poll_processesPendingJobs_whenCircuitClosed() {
    when(circuit.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    ValidationJob j1 = newPendingJob();
    ValidationJob j2 = newPendingJob();
    when(repo.findPickablePending(eq(MAX_PICKUP_ATTEMPTS), any(), any()))
        .thenReturn(List.of(j1, j2));
    // Both jobs will be loaded again by process() through findById
    when(repo.findById(j1.getId())).thenReturn(Optional.of(j1));
    when(repo.findById(j2.getId())).thenReturn(Optional.of(j2));
    stubHappyPath();

    worker.poll();

    verify(repo).findPickablePending(eq(MAX_PICKUP_ATTEMPTS), any(), any());
    verify(repo).findById(j1.getId());
    verify(repo).findById(j2.getId());
    verify(validator, times(2)).validate(any(ValidationRequest.class));
  }

  @Test
  void poll_appliesBackpressure_whenAllWorkersBusy() {
    // Executor that captures tasks but never runs them: the semaphore permits stay acquired,
    // modelling workers busy on long validations. The next poll must not claim more work.
    List<Runnable> captured = new ArrayList<>();
    ValidationWorker busyWorker =
        new ValidationWorker(
            repo,
            storage,
            profileService,
            applier,
            validator,
            om,
            registry,
            MAX_PICKUP_ATTEMPTS,
            java.time.Duration.ofMinutes(30),
            java.time.Duration.ofMinutes(5),
            captured::add,
            2,
            meterRegistry);
    when(circuit.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    when(repo.findPickablePending(eq(MAX_PICKUP_ATTEMPTS), any(), any()))
        .thenReturn(List.of(newPendingJob(), newPendingJob()));

    busyWorker.poll(); // claims 2 (all capacity), submits 2 tasks that never complete
    busyWorker.poll(); // no free capacity -> must not query the DB for more work

    assertThat(captured).hasSize(2);
    verify(repo, times(1)).findPickablePending(eq(MAX_PICKUP_ATTEMPTS), any(), any());
  }
}
