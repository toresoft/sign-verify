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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

@DataJpaTest
@ActiveProfiles("test")
class ValidationJobRepositoryTest {

  @Autowired private ValidationJobRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void count_pending_per_principal() {
    repo.save(newJob(JobStatus.PENDING, "alice"));
    repo.save(newJob(JobStatus.RUNNING, "alice"));
    repo.save(newJob(JobStatus.COMPLETED, "alice"));
    repo.save(newJob(JobStatus.PENDING, "bob"));
    em.flush();

    assertThat(repo.countActiveByPrincipal(PrincipalType.API_KEY, "alice")).isEqualTo(2);
    assertThat(repo.countActiveByPrincipal(PrincipalType.API_KEY, "bob")).isEqualTo(1);
  }

  @Test
  void pick_pending_for_processing_returns_oldest() {
    Instant base = Instant.now();
    ValidationJob j1 = newJob(JobStatus.PENDING, "alice");
    j1.setCreatedAt(base.minusSeconds(60));
    ValidationJob j2 = newJob(JobStatus.PENDING, "alice");
    j2.setCreatedAt(base.minusSeconds(30));
    repo.saveAll(List.of(j1, j2));
    em.flush();

    var picked = repo.findPickablePending(10, Instant.now(), 10);
    assertThat(picked).hasSize(2);
    assertThat(picked.get(0).getId()).isEqualTo(j1.getId());
  }

  @Test
  void expireOverdue_failsOnlyExpiredPendingAndRunning() {
    Instant past = Instant.now().minusSeconds(60);
    ValidationJob pendingExpired = newJob(JobStatus.PENDING, "alice");
    pendingExpired.setExpiresAt(past);
    pendingExpired.setCallbackUrl("https://callback.example/hook");
    ValidationJob runningExpired = newJob(JobStatus.RUNNING, "alice");
    runningExpired.setExpiresAt(past);
    ValidationJob completedExpired = newJob(JobStatus.COMPLETED, "alice");
    completedExpired.setExpiresAt(past);
    completedExpired.setResultPath("some/result.json");
    ValidationJob pendingFresh = newJob(JobStatus.PENDING, "alice");
    repo.saveAll(List.of(pendingExpired, runningExpired, completedExpired, pendingFresh));
    em.flush();

    int n = repo.expireOverdue(Instant.now());

    assertThat(n).isEqualTo(2);
    ValidationJob p = repo.findById(pendingExpired.getId()).orElseThrow();
    assertThat(p.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(p.getErrorCode()).isEqualTo(Errors.JOB_EXPIRED);
    assertThat(p.getErrorMessage()).isEqualTo("job expired before completion");
    assertThat(p.getCompletedAt()).isNotNull();
    assertThat(p.getNextCallbackAt()).isNotNull(); // has callbackUrl
    ValidationJob r = repo.findById(runningExpired.getId()).orElseThrow();
    assertThat(r.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(r.getNextCallbackAt()).isNull(); // no callbackUrl
    // Terminal and fresh jobs are untouched: the update is conditional on status+expiry.
    assertThat(repo.findById(completedExpired.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.COMPLETED);
    assertThat(repo.findById(pendingFresh.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.PENDING);
  }

  @Test
  void resetStaleRunning_requeuesOnlyStaleRunningJobs() {
    Instant now = Instant.now();
    ValidationJob stale = newJob(JobStatus.RUNNING, "alice");
    stale.setStartedAt(now.minusSeconds(3600));
    stale.setPickupAttempts(1);
    ValidationJob fresh = newJob(JobStatus.RUNNING, "alice");
    fresh.setStartedAt(now.minusSeconds(10));
    ValidationJob pending = newJob(JobStatus.PENDING, "alice");
    repo.saveAll(List.of(stale, fresh, pending));
    em.flush();

    int n = repo.resetStaleRunning(now.minusSeconds(1800));

    assertThat(n).isEqualTo(1);
    ValidationJob s = repo.findById(stale.getId()).orElseThrow();
    assertThat(s.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(s.getStartedAt()).isNull();
    // The pickup counter is NOT reset: crash-looping jobs stay bounded by max-pickup-attempts.
    assertThat(s.getPickupAttempts()).isEqualTo(1);
    assertThat(repo.findById(fresh.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.RUNNING);
    assertThat(repo.findById(pending.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.PENDING);
  }

  @Test
  void findPickablePending_skipsExpiredJobs() {
    ValidationJob expired = newJob(JobStatus.PENDING, "alice");
    expired.setExpiresAt(Instant.now().minusSeconds(60));
    ValidationJob fresh = newJob(JobStatus.PENDING, "alice");
    repo.saveAll(List.of(expired, fresh));
    em.flush();

    var picked = repo.findPickablePending(10, Instant.now(), 10);

    assertThat(picked).extracting(ValidationJob::getId).containsExactly(fresh.getId());
  }

  @Test
  void countByStatus_countsOnlyMatchingJobs() {
    repo.saveAll(
        List.of(
            newJob(JobStatus.PENDING, "alice"),
            newJob(JobStatus.PENDING, "bob"),
            newJob(JobStatus.RUNNING, "alice")));
    em.flush();

    assertThat(repo.countByStatus(JobStatus.PENDING)).isEqualTo(2);
    assertThat(repo.countByStatus(JobStatus.RUNNING)).isEqualTo(1);
    assertThat(repo.countByStatus(JobStatus.COMPLETED)).isZero();
  }

  @Test
  void claimCallback_claimsOnlyDueCallbacks_andParksThem() {
    Instant now = Instant.now();
    ValidationJob due = newJob(JobStatus.COMPLETED, "alice");
    due.setCallbackUrl("https://cb.example/hook");
    due.setNextCallbackAt(now.minusSeconds(5));
    ValidationJob notYet = newJob(JobStatus.COMPLETED, "alice");
    notYet.setCallbackUrl("https://cb.example/hook");
    notYet.setNextCallbackAt(now.plusSeconds(300));
    repo.saveAll(List.of(due, notYet));
    em.flush();
    em.clear();

    Instant parkUntil = now.plus(java.time.Duration.ofMinutes(5));
    assertThat(repo.claimCallback(due.getId(), now, parkUntil, 3)).isEqualTo(1);
    // Re-claim of the same row now sees the parked (future) nextCallbackAt -> no longer due.
    assertThat(repo.claimCallback(due.getId(), now, parkUntil, 3)).isZero();
    // A callback whose next attempt is in the future is not claimable.
    assertThat(repo.claimCallback(notYet.getId(), now, parkUntil, 3)).isZero();

    assertThat(repo.findById(due.getId()).orElseThrow().getNextCallbackAt())
        .isAfter(now.plus(java.time.Duration.ofMinutes(4)));
  }

  private ValidationJob newJob(JobStatus status, String principalId) {
    ValidationJob j = new ValidationJob();
    j.setId(UUID.randomUUID());
    j.setStatus(status);
    j.setReportsRequested("simple,etsi");
    j.setRequestedByPrincipalType(PrincipalType.API_KEY);
    j.setRequestedByPrincipalId(principalId);
    j.setCreatedAt(Instant.now());
    j.setExpiresAt(Instant.now().plusSeconds(86400));
    return j;
  }
}
