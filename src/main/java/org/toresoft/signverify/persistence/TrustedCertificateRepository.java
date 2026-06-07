package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.toresoft.signverify.domain.model.TrustedCertificate;

public interface TrustedCertificateRepository
    extends JpaRepository<TrustedCertificate, UUID>, JpaSpecificationExecutor<TrustedCertificate> {

  Optional<TrustedCertificate> findByFingerprintSha256(String fp);
}
