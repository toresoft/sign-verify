package org.toresoft.signverify.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

public interface ValidationJobRepository extends JpaRepository<ValidationJob, UUID> {

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

  @Query(
      """
      SELECT j FROM ValidationJob j
       WHERE j.status = 'PENDING'
         AND j.pickupAttempts < :maxAttempts
       ORDER BY j.createdAt ASC
      """)
  List<ValidationJob> findPickablePending(@Param("maxAttempts") int maxAttempts, Pageable pageable);

  default List<ValidationJob> findPickablePending(int maxAttempts, int limit) {
    return findPickablePending(maxAttempts, PageRequest.of(0, limit));
  }

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
}
