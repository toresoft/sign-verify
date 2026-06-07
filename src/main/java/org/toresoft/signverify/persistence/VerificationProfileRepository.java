package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.toresoft.signverify.domain.model.VerificationProfile;

public interface VerificationProfileRepository extends JpaRepository<VerificationProfile, UUID> {

  Optional<VerificationProfile> findByIsDefaultTrue();

  Optional<VerificationProfile> findByName(String name);
}
