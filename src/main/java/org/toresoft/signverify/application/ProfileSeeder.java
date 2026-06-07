package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@Component
public class ProfileSeeder {

  private final VerificationProfileRepository repo;
  private final PresetXmlLoader presetLoader;

  public ProfileSeeder(VerificationProfileRepository repo, PresetXmlLoader loader) {
    this.repo = repo;
    this.presetLoader = loader;
  }

  @EventListener
  public void onReady(ApplicationReadyEvent ev) {
    if (repo.count() > 0) return;
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName("STANDARD");
    p.setDescription("DSS default validation policy");
    p.setPreset(ProfilePreset.STANDARD);
    p.setPolicyXml(presetLoader.load(ProfilePreset.STANDARD));
    p.setIsDefault(true);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    repo.save(p);
  }
}
