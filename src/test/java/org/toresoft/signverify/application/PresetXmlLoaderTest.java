package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.toresoft.signverify.domain.model.ProfilePreset;

class PresetXmlLoaderTest {

  @Test
  void load_each_preset() throws Exception {
    for (ProfilePreset p :
        new ProfilePreset[] {ProfilePreset.BASIC, ProfilePreset.STANDARD, ProfilePreset.STRICT}) {
      var res = new ClassPathResource("policy/" + p.name() + ".xml");
      try (var in = res.getInputStream()) {
        String content = new String(in.readAllBytes());
        assertThat(content).contains("<ConstraintsParameters");
      }
    }
  }
}
