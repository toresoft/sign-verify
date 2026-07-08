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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

/**
 * DB-backed smoke tests for {@link JobCleanupScheduler}: each cleanup phase is exercised against
 * the real schema (H2 + Flyway) and real repository queries, with only the storage/audit
 * collaborators mocked. Unlike the Mockito-only tests, these survive internal refactorings of how
 * the scheduler queries the table.
 */
@DataJpaTest
@ActiveProfiles("test")
class JobCleanupSchedulerDbTest {

  private static final Duration INPUT_RETENTION = Duration.ofDays(7);
  private static final Duration RESULT_RETENTION = Duration.ofDays(30);
  private static final Duration TOMBSTONE_RETENTION = Duration.ofDays(90);

  @Autowired private ValidationJobRepository repo;
  @Autowired private TestEntityManager em;

  private DocumentStoragePort storage;
  private AuditService audit;
  private JobCleanupScheduler scheduler;

  @BeforeEach
  void setUp() {
    storage = mock(DocumentStoragePort.class);
    audit = mock(AuditService.class);
    scheduler =
        new JobCleanupScheduler(
            repo, storage, audit, INPUT_RETENTION, RESULT_RETENTION, TOMBSTONE_RETENTION);
  }

  private ValidationJob newJob(JobStatus status, Instant expiresAt) {
    ValidationJob j = new ValidationJob();
    j.setId(UUID.randomUUID());
    j.setStatus(status);
    j.setReportsRequested("simple");
    j.setCreatedAt(Instant.now().minus(Duration.ofHours(1)));
    j.setExpiresAt(expiresAt);
    j.setRequestedByPrincipalType(PrincipalType.API_KEY);
    j.setRequestedByPrincipalId("test-principal");
    return j;
  }

  private ValidationJob persist(ValidationJob j) {
    em.persistAndFlush(j);
    return j;
  }

  /** Runs the cleanup pass and detaches everything so assertions re-read from the DB. */
  private void runCleanup() {
    scheduler.cleanup();
    em.flush();
    em.clear();
  }

  private ValidationJob reload(ValidationJob j) {
    return repo.findById(j.getId()).orElseThrow();
  }

  // --- EXPIRED phase -----------------------------------------------------

  @Test
  void expiredPendingJob_isMarkedFailed() {
    ValidationJob j = persist(newJob(JobStatus.PENDING, Instant.now().minusSeconds(60)));

    runCleanup();

    ValidationJob r = reload(j);
    assertThat(r.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(r.getErrorCode()).isEqualTo(Errors.JOB_EXPIRED);
    assertThat(r.getErrorMessage()).isEqualTo("job expired before completion");
    assertThat(r.getCompletedAt()).isNotNull();
  }

  @Test
  void expiredRunningJob_isMarkedFailed() {
    ValidationJob j = persist(newJob(JobStatus.RUNNING, Instant.now().minusSeconds(60)));

    runCleanup();

    assertThat(reload(j).getStatus()).isEqualTo(JobStatus.FAILED);
  }

  @Test
  void expiredPendingJobWithCallback_getsNextCallbackAt() {
    ValidationJob j = newJob(JobStatus.PENDING, Instant.now().minusSeconds(60));
    j.setCallbackUrl("https://callback.example/hook");
    persist(j);

    runCleanup();

    assertThat(reload(j).getNextCallbackAt()).isNotNull();
  }

  @Test
  void freshPendingJob_isUntouched() {
    ValidationJob j = persist(newJob(JobStatus.PENDING, Instant.now().plusSeconds(3600)));

    runCleanup();

    ValidationJob r = reload(j);
    assertThat(r.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(r.getErrorMessage()).isNull();
  }

  @Test
  void expiredCompletedJob_isNotClobberedByExpiryPhase() {
    // A job that already reached a terminal state must never be flipped back to FAILED by the
    // expiry phase, even when expiresAt is in the past.
    ValidationJob j = newJob(JobStatus.COMPLETED, Instant.now().minusSeconds(60));
    j.setCompletedAt(Instant.now().minusSeconds(120));
    j.setResultPath("some/result.json");
    persist(j);

    runCleanup();

    ValidationJob r = reload(j);
    assertThat(r.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(r.getErrorMessage()).isNull();
    assertThat(r.getResultPath()).isEqualTo("some/result.json");
  }

  // --- INPUT phase --------------------------------------------------------

  @Test
  void terminalJobPastInputRetention_hasInputFileDeleted() {
    ValidationJob j = newJob(JobStatus.COMPLETED, Instant.now().plusSeconds(3600));
    j.setCompletedAt(Instant.now().minus(INPUT_RETENTION).minusSeconds(60));
    j.setDocumentPath("jobs/x/input-doc.pdf");
    persist(j);

    runCleanup();

    verify(storage).delete("jobs/x/input-doc.pdf");
    assertThat(reload(j).getDocumentPath()).isNull();
  }

  @Test
  void terminalJobWithinInputRetention_keepsInputFile() {
    ValidationJob j = newJob(JobStatus.COMPLETED, Instant.now().plusSeconds(3600));
    j.setCompletedAt(Instant.now().minusSeconds(60));
    j.setDocumentPath("jobs/y/input-doc.pdf");
    persist(j);

    runCleanup();

    assertThat(reload(j).getDocumentPath()).isEqualTo("jobs/y/input-doc.pdf");
  }

  // --- TOMBSTONE phase ----------------------------------------------------

  @Test
  void terminalJobPastResultRetention_isTombstoned() {
    ValidationJob j = newJob(JobStatus.COMPLETED, Instant.now().plusSeconds(3600));
    j.setCompletedAt(Instant.now().minus(RESULT_RETENTION).minusSeconds(60));
    j.setResultPath("jobs/z/result.json");
    j.setCallbackUrl("https://callback.example/hook");
    j.setCallbackSecretCipher("enc:v1:c");
    j.setProfileOverrides("{}");
    j.setErrorMessage("e");
    j.setErrorCode("internal-error");
    j.setLastCallbackError("cb");
    persist(j);

    runCleanup();

    verify(storage).delete("jobs/z/result.json");
    ValidationJob r = reload(j);
    assertThat(r.getStatus()).isEqualTo(JobStatus.DELETED);
    assertThat(r.getOriginalStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(r.getDeletedAt()).isNotNull();
    assertThat(r.getResultPath()).isNull();
    assertThat(r.getCallbackUrl()).isNull();
    assertThat(r.getCallbackSecretCipher()).isNull();
    assertThat(r.getProfileOverrides()).isNull();
    assertThat(r.getErrorMessage()).isNull();
    assertThat(r.getErrorCode()).isNull();
    assertThat(r.getLastCallbackError()).isNull();
  }

  // --- DELETE phase ---------------------------------------------------------

  @Test
  void tombstonePastRetention_isPhysicallyDeleted() {
    ValidationJob j = newJob(JobStatus.DELETED, Instant.now().plusSeconds(3600));
    j.setDeletedAt(Instant.now().minus(TOMBSTONE_RETENTION).minusSeconds(60));
    persist(j);

    runCleanup();

    assertThat(repo.findById(j.getId())).isEmpty();
  }

  @Test
  void recentTombstone_isKept() {
    ValidationJob j = newJob(JobStatus.DELETED, Instant.now().plusSeconds(3600));
    j.setDeletedAt(Instant.now().minusSeconds(60));
    persist(j);

    runCleanup();

    assertThat(repo.findById(j.getId())).isPresent();
  }
}
