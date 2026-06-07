package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  Optional<ApiKey> findByKeyPrefix(String prefix);

  Optional<ApiKey> findByName(String name);

  long countByRoleAndEnabled(Role role, boolean enabled);
}
