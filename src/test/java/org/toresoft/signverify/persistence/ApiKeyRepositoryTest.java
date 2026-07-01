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
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;

@DataJpaTest
@ActiveProfiles("test")
class ApiKeyRepositoryTest {

  @Autowired private ApiKeyRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void persist_and_load_by_prefix() {
    ApiKey k = newKey("alpha", "abc12345", Role.PRIVILEGED, true);
    repo.save(k);
    em.flush();
    em.clear();

    ApiKey loaded = repo.findByKeyPrefix("abc12345").orElseThrow();
    assertThat(loaded.getName()).isEqualTo("alpha");
    assertThat(loaded.getRole()).isEqualTo(Role.PRIVILEGED);
    assertThat(loaded.isEnabled()).isTrue();
  }

  @Test
  void count_privileged_enabled() {
    repo.save(newKey("p1", "p1prefix", Role.PRIVILEGED, true));
    repo.save(newKey("p2", "p2prefix", Role.PRIVILEGED, false));
    repo.save(newKey("s1", "s1prefix", Role.STANDARD, true));
    em.flush();

    assertThat(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).isEqualTo(1);
  }

  private ApiKey newKey(String name, String prefix, Role role, boolean enabled) {
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName(name);
    k.setKeyPrefix(prefix);
    k.setKeyHash("$2a$12$dummyhash");
    k.setRole(role);
    k.setEnabled(enabled);
    k.setBootstrap(false);
    k.setCreatedAt(Instant.now());
    return k;
  }
}
