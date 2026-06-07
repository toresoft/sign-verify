package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.TrustedCertificate;

@DataJpaTest
@ActiveProfiles("test")
class TrustedCertificateRepositoryTest {

  @Autowired private TrustedCertificateRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void find_by_fingerprint() {
    TrustedCertificate c = newCert("ab12", "IT", "Acme TSP");
    repo.save(c);
    em.flush();

    assertThat(repo.findByFingerprintSha256("ab12")).isPresent();
  }

  private TrustedCertificate newCert(String fp, String country, String tspName) {
    TrustedCertificate c = new TrustedCertificate();
    c.setId(UUID.randomUUID());
    c.setFingerprintSha256(fp);
    c.setCertificateDerB64("dGVzdA==");
    c.setCountry(country);
    c.setTspName(tspName);
    c.setLastSeenAt(Instant.now());
    return c;
  }
}
