package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

/**
 * Unit tests for {@link JobCleanupScheduler}.
 *
 * <p>All tests are pure Mockito unit tests; no Spring context is started. These tests are expected
 * to FAIL until the {@code JobCleanupScheduler} class is implemented (FASE 10, Task 10.1).
 */
@ExtendWith(MockitoExtension.class)
class JobCleanupSchedulerTest {

  private static final Duration INPUT_RETENTION = Duration.ofDays(7);
  private static final Duration RESULT_RETENTION = Duration.ofDays(30);
  private static final Duration TOMBSTONE_RETENTION = Duration.ofDays(90);
  private static final String DOC_PATH = "storage/input/abc.pdf";
  private static final String RESULT_PATH = "storage/result/abc.json";
  private static final String CALLBACK_URL = "https://callback.example/hook";

  @Mock private ValidationJobRepository repo;
  @Mock private DocumentStoragePort storage;

  private JobCleanupScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new JobCleanupScheduler(
            repo, storage, INPUT_RETENTION, RESULT_RETENTION, TOMBSTONE_RETENTION);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private ValidationJob newJob(JobStatus status) {
    return newJob(status, null, null, null, null);
  }

  private ValidationJob newJob(
      JobStatus status,
      Instant completedAt,
      Instant expiresAt,
      Instant deletedAt,
      String callbackUrl) {
    ValidationJob job = new ValidationJob();
    job.setId(UUID.randomUUID());
    job.setStatus(status);
    job.setDocumentPath(DOC_PATH);
    job.setResultPath(RESULT_PATH);
    job.setCompletedAt(completedAt);
    job.setExpiresAt(expiresAt);
    job.setDeletedAt(deletedAt);
    job.setCallbackUrl(callbackUrl);
    return job;
  }

  /** Captures the most recent entity passed to {@code repo.save(...)} and returns it. */
  private ValidationJob captureLastSave() {
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo, atLeastOnce()).save(captor.capture());
    List<ValidationJob> all = captor.getAllValues();
    return all.get(all.size() - 1);
  }

  private static org.mockito.verification.VerificationMode atLeastOnce() {
    return org.mockito.Mockito.atLeastOnce();
  }

  // ---------------------------------------------------------------------
  // Phase 4: EXPIRED — PENDING/RUNNING + expiresAt < now → FAILED
  // ---------------------------------------------------------------------

  @Test
  void cleanup_expiredPendingJob_marksFailed() {
    ValidationJob job = newJob(JobStatus.PENDING, null, Instant.now().minusSeconds(60), null, null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(saved.getErrorMessage()).isEqualTo("job_expired");
    assertThat(saved.getCompletedAt()).isNotNull();
  }

  @Test
  void cleanup_expiredRunningJob_marksFailed() {
    ValidationJob job = newJob(JobStatus.RUNNING, null, Instant.now().minusSeconds(60), null, null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(saved.getErrorMessage()).isEqualTo("job_expired");
    assertThat(saved.getCompletedAt()).isNotNull();
  }

  @Test
  void cleanup_nonExpiredJob_isUntouched() {
    ValidationJob job =
        newJob(JobStatus.PENDING, null, Instant.now().plus(Duration.ofMinutes(5)), null, null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(repo, never()).save(any());
  }

  @Test
  void cleanup_expiredJobWithCallback_setsNextCallbackAt() {
    ValidationJob job =
        newJob(JobStatus.PENDING, null, Instant.now().minusSeconds(60), null, CALLBACK_URL);
    when(repo.findAll()).thenReturn(List.of(job));

    Instant before = Instant.now();
    scheduler.cleanup();
    Instant after = Instant.now();

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(saved.getNextCallbackAt()).isNotNull();
    Instant minExpected = before;
    Instant maxExpected = after;
    assertThat(saved.getNextCallbackAt()).isBetween(minExpected, maxExpected);
  }

  // ---------------------------------------------------------------------
  // Phase 1: INPUT — terminal + completedAt < now - inputRetention
  // ---------------------------------------------------------------------

  @Test
  void cleanup_terminalJobOldEnough_deletesInputFile() {
    ValidationJob job =
        newJob(
            JobStatus.COMPLETED,
            Instant.now().minus(INPUT_RETENTION).minusSeconds(60),
            null,
            null,
            null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(storage, times(1)).delete(DOC_PATH);
    ValidationJob saved = captureLastSave();
    assertThat(saved.getDocumentPath()).isNull();
  }

  @Test
  void cleanup_terminalJobRecent_keepsInputFile() {
    ValidationJob job =
        newJob(
            JobStatus.COMPLETED,
            Instant.now().minus(INPUT_RETENTION).plusSeconds(60),
            null,
            null,
            null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(storage, never()).delete(DOC_PATH);
    verify(repo, never()).save(any());
  }

  // ---------------------------------------------------------------------
  // Phase 2: TOMBSTONE — terminal (not DELETED) + completedAt < now - resultRetention
  // ---------------------------------------------------------------------

  @Test
  void cleanup_terminalJobOldEnough_marksDeleted() {
    ValidationJob job =
        newJob(
            JobStatus.COMPLETED,
            Instant.now().minus(RESULT_RETENTION).minusSeconds(60),
            null,
            null,
            CALLBACK_URL);
    job.setCallbackSecretCipher("enc:v1:ciphertext");
    job.setProfileOverrides("{\"checkRevocation\":false}");
    job.setErrorMessage("old error");
    job.setLastCallbackError("old cb error");
    job.setOriginalStatus(null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(storage, times(1)).delete(RESULT_PATH);
    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.DELETED);
    assertThat(saved.getOriginalStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(saved.getDeletedAt()).isNotNull();
    assertThat(saved.getResultPath()).isNull();
    assertThat(saved.getCallbackUrl()).isNull();
    assertThat(saved.getCallbackSecretCipher()).isNull();
    assertThat(saved.getProfileOverrides()).isNull();
    assertThat(saved.getErrorMessage()).isNull();
    assertThat(saved.getLastCallbackError()).isNull();
  }

  // ---------------------------------------------------------------------
  // Phase 3: DELETE — DELETED + deletedAt < now - tombstoneRetention
  // ---------------------------------------------------------------------

  @Test
  void cleanup_alreadyDeletedJobOldEnough_deletesRow() {
    ValidationJob job =
        newJob(
            JobStatus.DELETED,
            null,
            null,
            Instant.now().minus(TOMBSTONE_RETENTION).minusSeconds(60),
            null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(repo, times(1)).deleteById(job.getId());
    verify(repo, never()).save(any());
  }

  @Test
  void cleanup_alreadyDeletedJobRecent_keepsRow() {
    ValidationJob job =
        newJob(
            JobStatus.DELETED,
            null,
            null,
            Instant.now().minus(TOMBSTONE_RETENTION).plusSeconds(60),
            null);
    lenient().when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(repo, never()).deleteById(any());
  }

  // ---------------------------------------------------------------------
  // Phase ordering — expired job that also matches tombstone runs Phase 4 first
  // ---------------------------------------------------------------------

  @Test
  void cleanup_phasesRunInOrder() {
    // Job is PENDING but expiresAt is past → Phase 4 marks it FAILED.
    // On a single pass, Phase 2 (tombstone) only runs on jobs whose completedAt
    // is older than resultRetention. Just-expiring jobs are not eligible.
    // We verify the FIRST pass only moves status PENDING → FAILED.
    ValidationJob job = newJob(JobStatus.PENDING, null, Instant.now().minusSeconds(60), null, null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(saved.getErrorMessage()).isEqualTo("job_expired");
    assertThat(saved.getStatus()).isNotEqualTo(JobStatus.DELETED);
  }

  @Test
  void cleanup_phasesRunInOrder_secondPass_marksDeleted() {
    // After the first cleanup pass the job is FAILED with completedAt = "just now".
    // A second pass observes it as terminal, completedAt is recent, so Phase 1
    // and Phase 2 are not triggered yet. To prove ordering we craft a job that is
    // ALREADY terminal AND already past the result retention window — the only
    // way this state is reachable from a PENDING job is via a prior cleanup
    // pass (Phase 4 → FAILED) followed by elapsed time → now Phase 2 deletes it.
    ValidationJob job =
        newJob(
            JobStatus.FAILED,
            Instant.now().minus(RESULT_RETENTION).minusSeconds(60),
            null,
            null,
            null);
    when(repo.findAll()).thenReturn(List.of(job));

    scheduler.cleanup();

    verify(storage, times(1)).delete(RESULT_PATH);
    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.DELETED);
    assertThat(saved.getOriginalStatus()).isEqualTo(JobStatus.FAILED);
  }

  // ---------------------------------------------------------------------
  // String-arg matching
  // ---------------------------------------------------------------------

}
