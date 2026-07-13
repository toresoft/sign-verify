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
package org.toresoft.signverify.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

public interface ValidationJobRepository extends JpaRepository<ValidationJob, UUID> {

  long countByStatus(JobStatus status);

  /**
   * Stamps {@code last_accessed_at} with a single-column UPDATE that does NOT bump {@code @Version}
   * (bulk JPQL bypasses the version column anyway). This is the same idiom as {@link
   * ApiKeyRepository#touchLastUsed} / {@link TrustedCertificateRepository#touchLastSeen}.
   *
   * <p>It exists to stop the polling endpoint ({@code GET /jobs/{id}}) from racing the worker: the
   * old {@code setLastAccessedAt + save} bumped the version in the DB, so a worker holding a copy
   * loaded at claim time saw {@link
   * org.springframework.orm.ObjectOptimisticLockingFailureException} on its final {@code save} and
   * dropped the validation result. A last-access stamp is metadata only and must not invalidate an
   * in-flight outcome.
   *
   * @return number of rows updated (1 = stamped, 0 = no such job)
   */
  @Modifying
  @Transactional
  @Query("UPDATE ValidationJob j SET j.lastAccessedAt = :now WHERE j.id = :id")
  int touchLastAccessed(@Param("id") UUID id, @Param("now") Instant now);

  /**
   * Atomically claims a job for processing by flipping {@code PENDING -> RUNNING} and incrementing
   * the pickup counter in a single conditional update. Returns 1 only for the worker that wins the
   * race; concurrent instances (multi-replica deployments) get 0 and must skip the job. This
   * prevents the same document from being validated twice and emitting duplicate callbacks.
   *
   * @return number of rows updated (1 = claimed by this caller, 0 = already claimed / not PENDING)
   */
  // @Transactional is required here: ValidationWorker.poll() calls process() via self-invocation,
  // so process()'s own @Transactional is bypassed and there is no ambient transaction for this
  // modifying query to join.
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING,
             j.startedAt = :now,
             j.pickupAttempts = j.pickupAttempts + 1
       WHERE j.id = :id
         AND j.status = org.toresoft.signverify.domain.model.JobStatus.PENDING
      """)
  int claimForProcessing(@Param("id") UUID id, @Param("now") Instant now);

  /**
   * Finalizes a validated job with a single conditional UPDATE: {@code RUNNING -> COMPLETED},
   * writes the result path and completion time, and arms the first callback when configured. The
   * version column is bumped explicitly (bulk JPQL bypasses {@code @Version}) so a worker that lost
   * the claim (or an admin that requeued/expired the job in the meantime) is surfaced as 0 rows
   * updated rather than silently clobbering the new state.
   *
   * <p>Conditional on {@code status = RUNNING}, this is idempotent and composes safely with {@link
   * #resetStaleRunning} and {@link #expireOverdue}: if the job was requeued or expired while the
   * worker validated, this update is a no-op (0 rows) and the worker logs and drops the result. It
   * never throws {@link org.springframework.orm.ObjectOptimisticLockingFailureException}, unlike
   * {@code save()} on a stale entity — the whole point of moving the worker's outcome writes off
   * the entity graph.
   *
   * @return number of rows updated (1 = finalized, 0 = no longer RUNNING)
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.COMPLETED,
             j.resultPath = :resultPath,
             j.completedAt = :now,
             j.nextCallbackAt = CASE WHEN j.callbackUrl IS NOT NULL THEN :now ELSE j.nextCallbackAt END,
             j.version = j.version + 1
       WHERE j.id = :id
         AND j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING
      """)
  int markCompleted(
      @Param("id") UUID id, @Param("resultPath") String resultPath, @Param("now") Instant now);

  /**
   * Marks a RUNNING job FAILED with a single conditional UPDATE, mirroring {@link #markCompleted}.
   * Terminal status is written with the catalog error code/message and the first callback armed
   * when configured. Conditional on {@code status = RUNNING} so it is a no-op if the job has
   * already been requeued (DSS retry) or expired by the cleanup pass. Never throws on a stale copy.
   *
   * @return number of rows updated (1 = marked FAILED, 0 = no longer RUNNING)
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.FAILED,
             j.errorCode = :errorCode,
             j.errorMessage = :errorMessage,
             j.completedAt = :now,
             j.nextCallbackAt = CASE WHEN j.callbackUrl IS NOT NULL THEN :now ELSE j.nextCallbackAt END,
             j.version = j.version + 1
       WHERE j.id = :id
         AND j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING
      """)
  int markFailed(
      @Param("id") UUID id,
      @Param("errorCode") String errorCode,
      @Param("errorMessage") String errorMessage,
      @Param("now") Instant now);

  /**
   * Requeues a RUNNING job back to PENDING for a transient retry (e.g. {@code dss.unavailable}):
   * clears {@code startedAt} (so startedAt-based "in progress" queries/SLA don't misread it) and
   * gives back the claim's pickup-attempt increment so transient DSS unavailability does not eat
   * the poison-job budget. Conditional on {@code status = RUNNING}: if the job was already requeued
   * by the stale-RUNNING sweep or expired, this is a no-op (0 rows).
   *
   * @return number of rows updated (1 = requeued, 0 = no longer RUNNING)
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.PENDING,
             j.startedAt = null,
             j.pickupAttempts = j.pickupAttempts - 1,
             j.version = j.version + 1
       WHERE j.id = :id
         AND j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING
      """)
  int requeueTransient(@Param("id") UUID id);

  @Query(
      """
      SELECT COUNT(j) FROM ValidationJob j
       WHERE j.requestedByPrincipalType = :type
         AND j.requestedByPrincipalId = :id
         AND j.status IN ('PENDING','RUNNING')
      """)
  long countActiveByPrincipal(@Param("type") PrincipalType type, @Param("id") String id);

  @Query(
      """
      SELECT COUNT(j) FROM ValidationJob j
       WHERE j.status IN ('PENDING','RUNNING')
      """)
  long countActiveGlobal();

  /**
   * Marks every overdue non-terminal job (PENDING/RUNNING past its {@code expiresAt}) as FAILED in
   * a single conditional UPDATE. Terminal jobs are never touched, so a COMPLETED result cannot be
   * clobbered by the cleanup pass racing a worker. The version column is bumped explicitly (bulk
   * JPQL bypasses {@code @Version}) so a worker holding a stale copy of an expired job fails with
   * an optimistic-lock conflict instead of silently resurrecting it.
   *
   * @return number of jobs expired
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.FAILED,
             j.errorCode = 'job.expired',
             j.errorMessage = 'job expired before completion',
             j.completedAt = :now,
             j.nextCallbackAt = CASE WHEN j.callbackUrl IS NOT NULL THEN :now ELSE j.nextCallbackAt END,
             j.version = j.version + 1
       WHERE j.status IN (org.toresoft.signverify.domain.model.JobStatus.PENDING,
                          org.toresoft.signverify.domain.model.JobStatus.RUNNING)
         AND j.expiresAt < :now
      """)
  int expireOverdue(@Param("now") Instant now);

  @Query(
      """
      SELECT j FROM ValidationJob j
       WHERE j.status = 'PENDING'
         AND j.pickupAttempts < :maxAttempts
         AND j.expiresAt > :now
       ORDER BY j.createdAt ASC
      """)
  List<ValidationJob> findPickablePending(
      @Param("maxAttempts") int maxAttempts, @Param("now") Instant now, Pageable pageable);

  default List<ValidationJob> findPickablePending(int maxAttempts, Instant now, int limit) {
    return findPickablePending(maxAttempts, now, PageRequest.of(0, limit));
  }

  /**
   * Requeues jobs stranded in RUNNING (e.g. by a JVM crash between the claim and the outcome
   * write): flips them back to PENDING so a worker can pick them up again. The pickup counter is
   * deliberately NOT reset — crash-looping (poison) jobs stay bounded by max-pickup-attempts. The
   * cutoff must be comfortably larger than the longest legitimate validation, or a slow job could
   * be claimed twice.
   *
   * @return number of jobs requeued
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.PENDING,
             j.startedAt = null,
             j.version = j.version + 1
       WHERE j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING
         AND j.startedAt < :cutoff
      """)
  int resetStaleRunning(@Param("cutoff") Instant cutoff);

  /** Terminal jobs whose input document is still on disk and past the input retention window. */
  @Query(
      """
      SELECT j FROM ValidationJob j
       WHERE j.status IN :statuses
         AND j.documentPath IS NOT NULL
         AND j.completedAt < :cutoff
       ORDER BY j.completedAt ASC
      """)
  List<ValidationJob> findWithPurgeableInput(
      @Param("statuses") Collection<JobStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);

  /** Terminal (non-DELETED) jobs past the result retention window, ready to become tombstones. */
  @Query(
      """
      SELECT j FROM ValidationJob j
       WHERE j.status IN :statuses
         AND j.completedAt < :cutoff
       ORDER BY j.completedAt ASC
      """)
  List<ValidationJob> findTombstoneable(
      @Param("statuses") Collection<JobStatus> statuses,
      @Param("cutoff") Instant cutoff,
      Pageable pageable);

  /**
   * Physically deletes tombstones past the tombstone retention window.
   *
   * @return number of rows deleted
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      DELETE FROM ValidationJob j
       WHERE j.status = org.toresoft.signverify.domain.model.JobStatus.DELETED
         AND j.deletedAt < :cutoff
      """)
  int deleteExpiredTombstones(@Param("cutoff") Instant cutoff);

  @Query(
      """
      SELECT j FROM ValidationJob j
       WHERE j.status IN ('COMPLETED','FAILED')
         AND j.callbackUrl IS NOT NULL
         AND j.nextCallbackAt <= :now
         AND j.callbackAttempts < :maxAttempts
       ORDER BY j.nextCallbackAt ASC
      """)
  List<ValidationJob> findCallbacksDue(
      @Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable pageable);

  default List<ValidationJob> findCallbacksDue(Instant now, int maxAttempts, int limit) {
    return findCallbacksDue(now, maxAttempts, PageRequest.of(0, limit));
  }

  /**
   * Atomically claims a due callback for dispatch by parking {@code nextCallbackAt} into the future
   * ({@code parkUntil}) in a single conditional update. Returns 1 only for the caller that wins the
   * race; concurrent polls (same or other instance) get 0 and must skip it. This lets callback
   * dispatch be offloaded to an executor without holding the {@code @SchedulerLock} for the whole
   * outbound HTTP call: the park keeps the callback invisible to {@code findCallbacksDue} until the
   * async dispatch overwrites {@code nextCallbackAt} with the real next attempt (or null). If the
   * instance dies mid-dispatch, the parked callback becomes due again after {@code parkUntil} and
   * is retried — the same crash-recovery contract the old lock-held loop had via {@code
   * lockAtMostFor}. The version column is bumped explicitly (bulk JPQL bypasses {@code @Version}).
   *
   * @return number of rows updated (1 = claimed by this caller, 0 = already claimed / not due)
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.nextCallbackAt = :parkUntil,
             j.version = j.version + 1
       WHERE j.id = :id
         AND j.status IN (org.toresoft.signverify.domain.model.JobStatus.COMPLETED,
                          org.toresoft.signverify.domain.model.JobStatus.FAILED)
         AND j.callbackUrl IS NOT NULL
         AND j.nextCallbackAt IS NOT NULL
         AND j.nextCallbackAt <= :now
         AND j.callbackAttempts < :maxAttempts
      """)
  int claimCallback(
      @Param("id") UUID id,
      @Param("now") Instant now,
      @Param("parkUntil") Instant parkUntil,
      @Param("maxAttempts") int maxAttempts);
}
