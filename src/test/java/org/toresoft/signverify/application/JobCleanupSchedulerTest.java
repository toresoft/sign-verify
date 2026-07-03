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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

/**
 * Unit tests for {@link JobCleanupScheduler}: delegation to the filtered repository queries and
 * per-phase side effects. End-to-end behavior against the real schema is covered by {@link
 * JobCleanupSchedulerDbTest}.
 */
@ExtendWith(MockitoExtension.class)
class JobCleanupSchedulerTest {

  private static final Duration INPUT_RETENTION = Duration.ofDays(7);
  private static final Duration RESULT_RETENTION = Duration.ofDays(30);
  private static final Duration TOMBSTONE_RETENTION = Duration.ofDays(90);
  private static final String DOC_PATH = "storage/input/abc.pdf";
  private static final String RESULT_PATH = "storage/result/abc.json";

  @Mock private ValidationJobRepository repo;
  @Mock private DocumentStoragePort storage;
  @Mock private AuditService audit;

  private JobCleanupScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new JobCleanupScheduler(
            repo, storage, audit, INPUT_RETENTION, RESULT_RETENTION, TOMBSTONE_RETENTION);
  }

  private ValidationJob terminalJob(JobStatus status) {
    ValidationJob job = new ValidationJob();
    job.setId(UUID.randomUUID());
    job.setStatus(status);
    job.setDocumentPath(DOC_PATH);
    job.setResultPath(RESULT_PATH);
    job.setCompletedAt(Instant.now().minus(Duration.ofDays(60)));
    return job;
  }

  @SuppressWarnings("unchecked")
  private List<ValidationJob> captureLastSaveAll() {
    ArgumentCaptor<List<ValidationJob>> captor = ArgumentCaptor.forClass(List.class);
    verify(repo, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
    return captor.getValue();
  }

  // --- EXPIRED phase: delegated to the conditional bulk update -------------

  @Test
  void cleanup_expiresOverdueJobs_viaConditionalUpdate() {
    when(repo.expireOverdue(any())).thenReturn(3);

    scheduler.cleanup();

    verify(repo, times(1)).expireOverdue(any(Instant.class));
    // Expiry must never load entities and re-save them: the conditional UPDATE is the whole phase.
    verify(repo, never()).findAll();
  }

  // --- INPUT phase ----------------------------------------------------------

  @Test
  void cleanup_purgesInputFiles_ofTerminalJobsPastRetention() {
    ValidationJob job = terminalJob(JobStatus.COMPLETED);
    when(repo.findWithPurgeableInput(any(), any(), any()))
        .thenReturn(List.of(job))
        .thenReturn(List.of());

    scheduler.cleanup();

    verify(storage, times(1)).delete(DOC_PATH);
    List<ValidationJob> saved = captureLastSaveAll();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).getDocumentPath()).isNull();
  }

  @Test
  void cleanup_inputPhase_usesInputRetentionCutoff() {
    Instant before = Instant.now().minus(INPUT_RETENTION);
    scheduler.cleanup();
    Instant after = Instant.now().minus(INPUT_RETENTION);

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(repo).findWithPurgeableInput(any(), cutoff.capture(), any());
    assertThat(cutoff.getValue()).isBetween(before, after);
  }

  // --- TOMBSTONE phase --------------------------------------------------------

  @Test
  void cleanup_tombstonesTerminalJobsPastResultRetention_scrubbingSensitiveFields() {
    ValidationJob job = terminalJob(JobStatus.COMPLETED);
    job.setCallbackUrl("https://callback.example/hook");
    job.setCallbackSecretCipher("enc:v1:ciphertext");
    job.setProfileOverrides("{\"checkRevocation\":false}");
    job.setErrorMessage("old error");
    job.setLastCallbackError("old cb error");
    when(repo.findTombstoneable(any(), any(), any()))
        .thenReturn(List.of(job))
        .thenReturn(List.of());

    scheduler.cleanup();

    verify(storage, times(1)).delete(RESULT_PATH);
    List<ValidationJob> saved = captureLastSaveAll();
    ValidationJob s = saved.get(0);
    assertThat(s.getStatus()).isEqualTo(JobStatus.DELETED);
    assertThat(s.getOriginalStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(s.getDeletedAt()).isNotNull();
    assertThat(s.getResultPath()).isNull();
    assertThat(s.getCallbackUrl()).isNull();
    assertThat(s.getCallbackSecretCipher()).isNull();
    assertThat(s.getProfileOverrides()).isNull();
    assertThat(s.getErrorMessage()).isNull();
    assertThat(s.getLastCallbackError()).isNull();
  }

  // --- DELETE phase -------------------------------------------------------------

  @Test
  void cleanup_deletesExpiredTombstones_viaBulkDelete() {
    when(repo.deleteExpiredTombstones(any())).thenReturn(2);

    scheduler.cleanup();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    verify(repo, times(1)).deleteExpiredTombstones(cutoff.capture());
    assertThat(cutoff.getValue()).isBeforeOrEqualTo(Instant.now().minus(TOMBSTONE_RETENTION));
    verify(repo, never()).deleteById(any());
  }

  // --- Audit summary ----------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void cleanup_auditsSummaryWithPhaseCounts() {
    when(repo.expireOverdue(any())).thenReturn(1);
    when(repo.findWithPurgeableInput(any(), any(), any()))
        .thenReturn(List.of(terminalJob(JobStatus.COMPLETED)))
        .thenReturn(List.of());
    when(repo.findTombstoneable(any(), any(), any()))
        .thenReturn(List.of(terminalJob(JobStatus.FAILED)))
        .thenReturn(List.of());
    when(repo.deleteExpiredTombstones(any())).thenReturn(4);

    scheduler.cleanup();

    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit, times(1))
        .log(
            eq(PrincipalType.SYSTEM),
            anyString(),
            eq(AuditActions.JOB_CLEANUP),
            eq("job"),
            isNull(),
            eq(true),
            details.capture());
    Map<String, Object> d = details.getValue();
    assertThat(d)
        .containsEntry("expired", 1)
        .containsEntry("inputDeleted", 1)
        .containsEntry("tombstoned", 1)
        .containsEntry("rowsDeleted", 4);
  }
}
