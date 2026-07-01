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
