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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class JobCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(JobCleanupScheduler.class);

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final AuditService audit;
  private final Duration inputRetention;
  private final Duration resultRetention;
  private final Duration tombstoneRetention;

  public JobCleanupScheduler(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      AuditService audit,
      @Value("${app.async.input-retention}") Duration inputRetention,
      @Value("${app.async.result-retention}") Duration resultRetention,
      @Value("${app.async.tombstone-retention}") Duration tombstoneRetention) {
    this.repo = repo;
    this.storage = storage;
    this.audit = audit;
    this.inputRetention = inputRetention;
    this.resultRetention = resultRetention;
    this.tombstoneRetention = tombstoneRetention;
  }

  @Scheduled(cron = "${app.async.cleanup.cron}")
  @SchedulerLock(name = "jobCleanup", lockAtMostFor = "PT30M")
  @Transactional
  public void cleanup() {
    Instant now = Instant.now();
    int expired = 0;
    int inputDeleted = 0;
    int tombstoned = 0;
    int rowsDeleted = 0;

    // Fase 4 EXPIRED: PENDING/RUNNING + expires_at < now → FAILED
    for (var j : repo.findAll()) {
      if ((j.getStatus() == JobStatus.PENDING || j.getStatus() == JobStatus.RUNNING)
          && j.getExpiresAt().isBefore(now)) {
        j.setStatus(JobStatus.FAILED);
        j.setErrorMessage("job_expired");
        j.setCompletedAt(now);
        if (j.getCallbackUrl() != null) j.setNextCallbackAt(now);
        repo.save(j);
        expired++;
      }
    }

    // Fase 1 INPUT: terminal + completed_at < now-inputRetention → delete input file
    for (var j : repo.findAll()) {
      if (j.getStatus().isTerminal()
          && j.getDocumentPath() != null
          && j.getCompletedAt() != null
          && j.getCompletedAt().isBefore(now.minus(inputRetention))) {
        storage.delete(j.getDocumentPath());
        j.setDocumentPath(null);
        repo.save(j);
        inputDeleted++;
      }
    }

    // Fase 2 TOMBSTONE: terminal (not DELETED) + completed_at < now-resultRetention
    for (var j : repo.findAll()) {
      if (j.getStatus().isTerminal()
          && j.getStatus() != JobStatus.DELETED
          && j.getCompletedAt() != null
          && j.getCompletedAt().isBefore(now.minus(resultRetention))) {
        storage.delete(j.getResultPath());
        j.setOriginalStatus(j.getStatus());
        j.setStatus(JobStatus.DELETED);
        j.setDeletedAt(now);
        j.setResultPath(null);
        j.setCallbackUrl(null);
        j.setCallbackSecretCipher(null);
        j.setProfileOverrides(null);
        j.setErrorMessage(null);
        j.setLastCallbackError(null);
        repo.save(j);
        tombstoned++;
      }
    }

    // Fase 3 DELETE: DELETED + deleted_at < now-tombstoneRetention → DELETE row
    for (var j : repo.findAll()) {
      if (j.getStatus() == JobStatus.DELETED
          && j.getDeletedAt() != null
          && j.getDeletedAt().isBefore(now.minus(tombstoneRetention))) {
        repo.deleteById(j.getId());
        rowsDeleted++;
      }
    }

    log.info(
        "Cleanup pass complete at {} (expired={}, inputDeleted={}, tombstoned={}, rowsDeleted={})",
        now,
        expired,
        inputDeleted,
        tombstoned,
        rowsDeleted);

    // Record a single summary event so operators have a quick "did the cleanup run?" signal.
    // The actor is SYSTEM; we do not log per-job IDs here to keep the row small and avoid PII.
    Map<String, Object> details = new HashMap<>();
    details.put("expired", expired);
    details.put("inputDeleted", inputDeleted);
    details.put("tombstoned", tombstoned);
    details.put("rowsDeleted", rowsDeleted);
    audit.log(
        PrincipalType.SYSTEM,
        "scheduler-job-cleanup",
        AuditActions.JOB_CLEANUP,
        "job",
        null,
        true,
        details);
  }
}
