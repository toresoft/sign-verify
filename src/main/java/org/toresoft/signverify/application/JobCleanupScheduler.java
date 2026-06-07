package org.toresoft.signverify.application;

import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class JobCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(JobCleanupScheduler.class);

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final Duration inputRetention;
  private final Duration resultRetention;
  private final Duration tombstoneRetention;

  public JobCleanupScheduler(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      @Value("${app.async.input-retention}") Duration inputRetention,
      @Value("${app.async.result-retention}") Duration resultRetention,
      @Value("${app.async.tombstone-retention}") Duration tombstoneRetention) {
    this.repo = repo;
    this.storage = storage;
    this.inputRetention = inputRetention;
    this.resultRetention = resultRetention;
    this.tombstoneRetention = tombstoneRetention;
  }

  @Scheduled(cron = "${app.async.cleanup.cron}")
  @SchedulerLock(name = "jobCleanup", lockAtMostFor = "PT30M")
  @Transactional
  public void cleanup() {
    Instant now = Instant.now();

    // Fase 4 EXPIRED: PENDING/RUNNING + expires_at < now → FAILED
    repo.findAll().stream()
        .filter(
            j ->
                (j.getStatus() == JobStatus.PENDING || j.getStatus() == JobStatus.RUNNING)
                    && j.getExpiresAt().isBefore(now))
        .forEach(
            j -> {
              j.setStatus(JobStatus.FAILED);
              j.setErrorMessage("job_expired");
              j.setCompletedAt(now);
              if (j.getCallbackUrl() != null) j.setNextCallbackAt(now);
              repo.save(j);
            });

    // Fase 1 INPUT: terminal + completed_at < now-inputRetention → delete input file
    repo.findAll().stream()
        .filter(
            j ->
                j.getStatus().isTerminal()
                    && j.getDocumentPath() != null
                    && j.getCompletedAt() != null
                    && j.getCompletedAt().isBefore(now.minus(inputRetention)))
        .forEach(
            j -> {
              storage.delete(j.getDocumentPath());
              j.setDocumentPath(null);
              repo.save(j);
            });

    // Fase 2 TOMBSTONE: terminal (not DELETED) + completed_at < now-resultRetention
    repo.findAll().stream()
        .filter(
            j ->
                j.getStatus().isTerminal()
                    && j.getStatus() != JobStatus.DELETED
                    && j.getCompletedAt() != null
                    && j.getCompletedAt().isBefore(now.minus(resultRetention)))
        .forEach(
            j -> {
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
            });

    // Fase 3 DELETE: DELETED + deleted_at < now-tombstoneRetention → DELETE row
    repo.findAll().stream()
        .filter(
            j ->
                j.getStatus() == JobStatus.DELETED
                    && j.getDeletedAt() != null
                    && j.getDeletedAt().isBefore(now.minus(tombstoneRetention)))
        .forEach(j -> repo.deleteById(j.getId()));

    log.info("Cleanup pass complete at {}", now);
  }
}
