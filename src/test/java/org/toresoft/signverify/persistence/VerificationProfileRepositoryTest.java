package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;

@DataJpaTest
@ActiveProfiles("test")
class VerificationProfileRepositoryTest {

  @Autowired private VerificationProfileRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void find_default() {
    VerificationProfile def = newProfile("STANDARD", ProfilePreset.STANDARD, true);
    VerificationProfile other = newProfile("STRICT", ProfilePreset.STRICT, false);
    repo.saveAll(java.util.List.of(def, other));
    em.flush();
    em.clear();

    assertThat(repo.findByIsDefaultTrue())
        .isPresent()
        .get()
        .extracting(VerificationProfile::getName)
        .isEqualTo("STANDARD");
  }

  private VerificationProfile newProfile(String name, ProfilePreset preset, boolean isDefault) {
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName(name);
    p.setPreset(preset);
    p.setPolicyXml("<ConstraintsParameters/>");
    p.setIsDefault(isDefault);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    return p;
  }
}
