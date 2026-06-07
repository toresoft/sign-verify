package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

  @Autowired private AuditLogRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void persist_and_query() {
    AuditLog log = new AuditLog();
    log.setId(UUID.randomUUID());
    log.setOccurredAt(Instant.now());
    log.setPrincipalType(PrincipalType.API_KEY);
    log.setPrincipalId("k1");
    log.setAction("API_KEY_CREATE");
    log.setSuccess(true);
    repo.save(log);
    em.flush();

    assertThat(repo.count()).isEqualTo(1);
  }
}
