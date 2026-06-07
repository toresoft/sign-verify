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

    var picked = repo.findPickablePending(10, 10);
    assertThat(picked).hasSize(2);
    assertThat(picked.get(0).getId()).isEqualTo(j1.getId());
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
