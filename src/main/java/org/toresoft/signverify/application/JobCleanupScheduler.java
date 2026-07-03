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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;
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

  // Page size for the phases that must load entities (side effects on storage). The processed
  // rows leave the query filter (documentPath nulled / status flipped), so each phase re-fetches
  // page 0 until it comes back empty and never holds more than one page in memory.
  private static final int PAGE_SIZE = 200;

  private static final Set<JobStatus> TERMINAL =
      EnumSet.of(
          JobStatus.COMPLETED,
          JobStatus.FAILED,
          JobStatus.DELIVERED,
          JobStatus.DELIVERY_FAILED,
          JobStatus.DELETED);
  private static final Set<JobStatus> TOMBSTONEABLE =
      EnumSet.of(
          JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.DELIVERED, JobStatus.DELIVERY_FAILED);

  // Deliberately not @Transactional: each phase commits on its own (bulk statements and per-page
  // saves), so a large backlog never turns into one long-held transaction.
  @Scheduled(cron = "${app.async.cleanup.cron}")
  @SchedulerLock(name = "jobCleanup", lockAtMostFor = "PT30M")
  public void cleanup() {
    Instant now = Instant.now();

    // EXPIRED: PENDING/RUNNING past expires_at → FAILED, via a single conditional UPDATE that can
    // never touch terminal jobs (no race with a worker completing the job concurrently).
    int expired = repo.expireOverdue(now);

    // INPUT: terminal + completed_at < now-inputRetention → delete input file
    int inputDeleted = 0;
    List<ValidationJob> page;
    while (!(page = repo.findWithPurgeableInput(TERMINAL, now.minus(inputRetention), firstPage()))
        .isEmpty()) {
      for (ValidationJob j : page) {
        storage.delete(j.getDocumentPath());
        j.setDocumentPath(null);
      }
      repo.saveAll(page);
      inputDeleted += page.size();
    }

    // TOMBSTONE: terminal (not DELETED) + completed_at < now-resultRetention
    int tombstoned = 0;
    while (!(page = repo.findTombstoneable(TOMBSTONEABLE, now.minus(resultRetention), firstPage()))
        .isEmpty()) {
      for (ValidationJob j : page) {
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
      }
      repo.saveAll(page);
      tombstoned += page.size();
    }

    // DELETE: DELETED + deleted_at < now-tombstoneRetention → bulk DELETE
    int rowsDeleted = repo.deleteExpiredTombstones(now.minus(tombstoneRetention));

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

  private static Pageable firstPage() {
    return PageRequest.of(0, PAGE_SIZE);
  }
}
