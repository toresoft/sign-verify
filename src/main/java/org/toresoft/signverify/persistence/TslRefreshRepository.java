package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.toresoft.signverify.domain.model.TslRefresh;

public interface TslRefreshRepository extends JpaRepository<TslRefresh, UUID> {

  Optional<TslRefresh> findTopByOrderByStartedAtDesc();
}
