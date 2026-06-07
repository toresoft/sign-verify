package org.toresoft.signverify.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  Optional<ApiKey> findByKeyPrefix(String prefix);

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
