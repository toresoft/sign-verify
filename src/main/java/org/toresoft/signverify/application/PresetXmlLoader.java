package org.toresoft.signverify.application;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ProfilePreset;

@Component
public class PresetXmlLoader {

  public String load(ProfilePreset preset) {
    try (var in = new ClassPathResource("policy/" + preset.name() + ".xml").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("missing policy: " + preset, e);
    }
  }
}
