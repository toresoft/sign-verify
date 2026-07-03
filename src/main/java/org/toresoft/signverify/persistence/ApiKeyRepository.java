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

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  Optional<ApiKey> findByKeyPrefix(String prefix);

  /**
   * Stamps last_used_at with a single-column UPDATE (no entity load, safe on the hot auth path).
   * {@code @Transactional} is required here: the auth filter has no ambient transaction.
   */
  @Modifying
  @Transactional
  @Query("update ApiKey k set k.lastUsedAt = :now where k.id = :id")
  int touchLastUsed(@Param("id") UUID id, @Param("now") Instant now);

  Optional<ApiKey> findByName(String name);

  long countByRoleAndEnabled(Role role, boolean enabled);

  /**
   * Acquires a write lock on the enabled keys of a role so the "last privileged key" invariant can
   * be checked without a TOCTOU race: concurrent delete/disable transactions serialize on these
   * rows instead of both observing a stale count.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select k.id from ApiKey k where k.role = :role and k.enabled = true")
  List<UUID> lockEnabledIdsByRole(@Param("role") Role role);
}
