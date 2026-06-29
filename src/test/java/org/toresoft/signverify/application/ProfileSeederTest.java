package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
class ProfileSeederTest {

  @Autowired private VerificationProfileRepository repo;

  @Test
  void seeds_agid_preset_without_timestamp() {
    var agid = repo.findByName("agid").orElseThrow();
    assertThat(agid.getPreset()).isEqualTo(ProfilePreset.AGID);
    assertThat(agid.getIsDefault()).isFalse();
    // QES (firma digitale): qualified cert on a QSCD, CA/QC trust service.
    assertThat(agid.getPolicyXml())
        .contains("<QcCompliance Level=\"FAIL\" />")
        .contains("<QcSSCD Level=\"FAIL\" />")
        .contains("<TrustServiceTypeIdentifier Level=\"FAIL\">")
        .doesNotContain("<TLevelTimeStamp");
  }

  @Test
  void seeds_agid_ts_preset_requiring_timestamp() {
    var agidTs = repo.findByName("agid-ts").orElseThrow();
    assertThat(agidTs.getPreset()).isEqualTo(ProfilePreset.AGID_TS);
    assertThat(agidTs.getIsDefault()).isFalse();
    assertThat(agidTs.getPolicyXml()).contains("<TLevelTimeStamp Level=\"FAIL\" />");
  }

  @Test
  void default_profile_remains_standard() {
    var def = repo.findByIsDefaultTrue().orElseThrow();
    assertThat(def.getPreset()).isEqualTo(ProfilePreset.STANDARD);
  }
}
