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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;
import org.toresoft.signverify.persistence.ValidationJobRepository;

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

  private final ObjectMapper om = new ObjectMapper();

  private ValidationWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new ValidationWorker(
            repo, storage, profileService, applier, validator, om, registry, MAX_PICKUP_ATTEMPTS);
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
   * Captures all entities passed to {@code repo.save(...)} during a single call and returns the
   * last one.
   */
  private ValidationJob captureLastSave() {
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo, atLeastOnce()).save(captor.capture());
    List<ValidationJob> all = captor.getAllValues();
    return all.get(all.size() - 1);
  }

  private static org.mockito.verification.VerificationMode atLeastOnce() {
    return org.mockito.Mockito.atLeastOnce();
  }

  /**
   * Stubs the typical happy-path collaborator chain: storage.read, profile lookup, validator.
   *
   * @return the returned {@link ValidationResult} for further assertions
   */
  private ValidationResult stubHappyPath() {
    when(storage.read(anyString())).thenReturn(new byte[] {1, 2, 3});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    ValidationResult result = validationResult();
    when(validator.validate(any(ValidationRequest.class))).thenReturn(result);
    when(storage.storeResult(anyString(), any())).thenReturn("storage/result/path");
    return result;
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

    ValidationJob last = captureLastSave();
    assertThat(last.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(last.getResultPath()).isEqualTo("storage/result/path");
    assertThat(last.getCompletedAt()).isNotNull();
    assertThat(last.getErrorMessage()).isNull();

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

    ValidationJob last = captureLastSave();
    assertThat(last.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(last.getErrorMessage()).isNull();
    assertThat(last.getCompletedAt()).isNull();
    // The in-progress marker must be cleared so the requeued job is not seen as running.
    assertThat(last.getStartedAt()).isNull();
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

    ValidationJob last = captureLastSave();
    assertThat(last.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(last.getErrorMessage()).isNotBlank();
    assertThat(last.getCompletedAt()).isNotNull();
  }

  @Test
  void process_validatorException_marksFailed() {
    ValidationJob job = newPendingJob();
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    when(storage.read(anyString())).thenReturn(new byte[] {0});
    when(profileService.getOrDefault(PROFILE_ID)).thenReturn(profile());
    when(validator.validate(any(ValidationRequest.class))).thenThrow(new RuntimeException("boom"));

    worker.process(job.getId());

    ValidationJob last = captureLastSave();
    assertThat(last.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(last.getErrorMessage()).contains("boom");
    assertThat(last.getCompletedAt()).isNotNull();
  }

  @Test
  void process_setsNextCallbackAt_whenCallbackUrlSet() {
    ValidationJob job = newPendingJob("https://callback.example/hook", null);
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    stubHappyPath();

    worker.process(job.getId());

    ValidationJob last = captureLastSave();
    assertThat(last.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(last.getNextCallbackAt()).isNotNull();
  }

  @Test
  void process_noCallbackUrl_doesNotSetNextCallbackAt() {
    ValidationJob job = newPendingJob(null, null);
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    stubHappyPath();

    worker.process(job.getId());

    ValidationJob last = captureLastSave();
    assertThat(last.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(last.getNextCallbackAt()).isNull();
  }

  // ---------------------------------------------------------------------
  // poll() tests
  // ---------------------------------------------------------------------

  @Test
  void poll_skipsWhenCircuitOpen() {
    when(circuit.getState()).thenReturn(CircuitBreaker.State.OPEN);

    worker.poll();

    verify(repo, never()).findPickablePending(anyInt(), any());
    verify(validator, never()).validate(any());
  }

  @Test
  void poll_processesPendingJobs_whenCircuitClosed() {
    when(circuit.getState()).thenReturn(CircuitBreaker.State.CLOSED);
    ValidationJob j1 = newPendingJob();
    ValidationJob j2 = newPendingJob();
    when(repo.findPickablePending(eq(MAX_PICKUP_ATTEMPTS), any())).thenReturn(List.of(j1, j2));
    // Both jobs will be loaded again by process() through findById
    when(repo.findById(j1.getId())).thenReturn(Optional.of(j1));
    when(repo.findById(j2.getId())).thenReturn(Optional.of(j2));
    stubHappyPath();

    worker.poll();

    verify(repo).findPickablePending(eq(MAX_PICKUP_ATTEMPTS), any());
    verify(repo).findById(j1.getId());
    verify(repo).findById(j2.getId());
    verify(validator, times(2)).validate(any(ValidationRequest.class));
  }
}
