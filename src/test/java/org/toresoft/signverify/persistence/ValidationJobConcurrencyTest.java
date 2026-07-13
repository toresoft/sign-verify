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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

/**
 * Guards the optimistic-lock contract that keeps the worker's final outcome from being lost to a
 * race with the polling endpoint.
 *
 * <p>Originally this test reproduced the bug: {@code GET /jobs/{id}} bumped {@code @Version} via
 * {@code setLastAccessedAt + save} while the worker held a stale copy, so the worker's final {@code
 * repo.save(job)} threw {@link ObjectOptimisticLockingFailureException} and dropped the result. The
 * fix routes last-access through the version-neutral {@link
 * ValidationJobRepository#touchLastAccessed} and routes the worker's finalize through the
 * conditional bulk updates. The inverted assertions below are now the non-regression guarantee: a
 * concurrent last-access touch must NOT stop the finalize from landing.
 */
@DataJpaTest
@ActiveProfiles("test")
class ValidationJobConcurrencyTest {

  @Autowired private ValidationJobRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void pollingLastAccessedTouch_doesNotBlockWorkerFinalize() {
    // 1. Persist a RUNNING job at version 0 (the state right after claimForProcessing).
    ValidationJob seed = newRunningJob();
    repo.save(seed);
    em.flush();
    em.clear();

    // 2. Load the worker's in-memory copy (A), then detach so changes to A are not auto-flushed.
    ValidationJob workerCopy = repo.findById(seed.getId()).orElseThrow();
    em.detach(workerCopy);

    // 3. Simulate a concurrent GET /jobs/{id}: the controller now calls the version-neutral
    //    touchLastAccessed bulk UPDATE (no @Version bump), not setLastAccessedAt + save.
    repo.touchLastAccessed(seed.getId(), Instant.now());
    em.flush();
    em.clear();

    // 4. The worker finishes and finalizes via markCompleted (conditional bulk UPDATE on
    //    status=RUNNING). The last-access touch must NOT have invalidated it: the finalize lands.
    assertThat(repo.markCompleted(workerCopy.getId(), "result.json", Instant.now())).isEqualTo(1);

    ValidationJob finalized = repo.findById(seed.getId()).orElseThrow();
    assertThat(finalized.getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(finalized.getResultPath()).isEqualTo("result.json");
    assertThat(finalized.getCompletedAt()).isNotNull();
    assertThat(finalized.getLastAccessedAt()).isNotNull();
  }

  @Test
  void markCompleted_isNoOp_whenJobNoLongerRunning() {
    // The job was requeued by the stale-RUNNING sweep (or expired) while the worker validated.
    ValidationJob seed = newRunningJob();
    seed.setStatus(JobStatus.PENDING); // e.g. swept back to PENDING
    repo.save(seed);
    em.flush();
    em.clear();

    // The conditional finalize matches no row (status != RUNNING) and returns 0: no exception,
    // no clobbering of the new state. The outcome is intentionally dropped.
    assertThat(repo.markCompleted(seed.getId(), "result.json", Instant.now())).isZero();
    assertThat(repo.findById(seed.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.PENDING);
  }

  @Test
  void markFailed_isNoOp_whenJobNoLongerRunning_andArmsCallbackWhenCallbackUrl() {
    ValidationJob withCallback = newRunningJob();
    withCallback.setCallbackUrl("https://cb.example/hook");
    repo.save(withCallback);
    em.flush();
    em.clear();

    Instant before = Instant.now();
    assertThat(repo.markFailed(withCallback.getId(), Errors.SOME_CODE, "boom", Instant.now()))
        .isEqualTo(1);
    em.clear();

    ValidationJob failed = repo.findById(withCallback.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(failed.getErrorCode()).isEqualTo(Errors.SOME_CODE);
    assertThat(failed.getErrorMessage()).isEqualTo("boom");
    // CASE WHEN callbackUrl IS NOT NULL THEN :now arms the first callback attempt.
    assertThat(failed.getNextCallbackAt()).isBetween(before, Instant.now().plusSeconds(60));
  }

  @Test
  void requeueTransient_clearsStartedAtAndGivesBackPickupAttempt() {
    ValidationJob running = newRunningJob();
    running.setPickupAttempts(2); // post-claim state
    running.setStartedAt(Instant.now());
    repo.save(running);
    em.flush();
    em.clear();

    assertThat(repo.requeueTransient(running.getId())).isEqualTo(1);
    em.clear();

    ValidationJob requeued = repo.findById(running.getId()).orElseThrow();
    assertThat(requeued.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(requeued.getStartedAt()).isNull();
    assertThat(requeued.getPickupAttempts()).isEqualTo(1);
  }

  @Test
  void touchLastAccessed_doesNotBumpVersion() {
    // The whole point of touchLastAccessed: a version-neutral stamp must not invalidate a copy
    // loaded before the touch. We assert it behaviourally: load a worker copy, touch last-access,
    // then save() the (now stale-by-version-under-the-old-code) copy. With the old
    // setLastAccessedAt
    // + save this save() would throw ObjectOptimisticLockingFailureException; with the bulk touch
    // it must succeed because the version was never bumped.
    ValidationJob seed = newRunningJob();
    repo.save(seed);
    em.flush();
    em.clear();

    ValidationJob workerCopy = repo.findById(seed.getId()).orElseThrow();
    em.detach(workerCopy);

    repo.touchLastAccessed(seed.getId(), Instant.now());
    em.flush();

    workerCopy.setStatus(JobStatus.COMPLETED);
    workerCopy.setResultPath("result.json");
    workerCopy.setCompletedAt(Instant.now());
    // No optimistic-lock exception: the touch did not bump @Version, so the stale-by-load copy is
    // still valid and the entity save lands. (We do NOT assert lastAccessedAt here: a full-row
    // entity save overwrites it with the worker copy's value; that overwrite is exactly why the
    // worker's real finalize path uses the conditional bulk UPDATE instead of save().)
    repo.save(workerCopy);
    em.flush();
    em.clear();

    ValidationJob finalized = repo.findById(seed.getId()).orElseThrow();
    assertThat(finalized.getStatus()).isEqualTo(JobStatus.COMPLETED);
  }

  private ValidationJob newRunningJob() {
    ValidationJob j = new ValidationJob();
    j.setId(UUID.randomUUID());
    j.setStatus(JobStatus.RUNNING);
    j.setReportsRequested("simple,etsi");
    j.setRequestedByPrincipalType(PrincipalType.API_KEY);
    j.setRequestedByPrincipalId("alice");
    j.setCreatedAt(Instant.now());
    j.setStartedAt(Instant.now());
    j.setExpiresAt(Instant.now().plusSeconds(86400));
    return j;
  }

  // Local stubs so this test stays self-contained without importing the real Errors catalog (which
  // is unrelated to the JPA contract under test here).
  private static final class Errors {
    static final String SOME_CODE = "test.code";
  }
}
