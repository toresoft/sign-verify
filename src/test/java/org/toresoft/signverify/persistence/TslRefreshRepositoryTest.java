package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.RefreshStatus;
import org.toresoft.signverify.domain.model.RefreshTrigger;
import org.toresoft.signverify.domain.model.TslRefresh;

@DataJpaTest
@ActiveProfiles("test")
class TslRefreshRepositoryTest {

  @Autowired private TslRefreshRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void find_latest() {
    TslRefresh older = newRefresh(Instant.parse("2026-06-01T00:00:00Z"));
    TslRefresh newer = newRefresh(Instant.parse("2026-06-02T00:00:00Z"));
    repo.saveAll(java.util.List.of(older, newer));
    em.flush();

    assertThat(repo.findTopByOrderByStartedAtDesc())
        .isPresent()
        .get()
        .extracting(TslRefresh::getId)
        .isEqualTo(newer.getId());
  }

  private TslRefresh newRefresh(Instant startedAt) {
    TslRefresh r = new TslRefresh();
    r.setId(UUID.randomUUID());
    r.setTrigger(RefreshTrigger.SCHEDULED);
    r.setStartedAt(startedAt);
    r.setStatus(RefreshStatus.SUCCESS);
    return r;
  }
}
