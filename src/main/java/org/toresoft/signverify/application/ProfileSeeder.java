package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@Component
public class ProfileSeeder {

  private static final Logger log = LoggerFactory.getLogger(ProfileSeeder.class);

  private final VerificationProfileRepository repo;
  private final PresetXmlLoader presetLoader;

  public ProfileSeeder(VerificationProfileRepository repo, PresetXmlLoader loader) {
    this.repo = repo;
    this.presetLoader = loader;
  }

  @EventListener
  public void onReady(ApplicationReadyEvent ev) {
    // Idempotent w.r.t. the DEFAULT profile specifically, not the total row count.
    //
    // Why: the IT suite shares a single in-memory H2 instance across Spring
    // contexts (url is 'jdbc:h2:mem:test', generate-unique-name is overridden
    // by the explicit url). An earlier IT (e.g. AuditLogIT) may insert a
    // CUSTOM profile with stub XML ('<policy/>', 9 bytes) before
    // VerificationControllerIT starts. The previous 'count() > 0' guard
    // skipped seeding in that case, leaving getOrDefault() returning the
    // stub and DSS rejecting it on the runner with HTTP 400 'invalid
    // validation policy'. Checking for the default profile ensures the
    // standard DSS policy is always available regardless of other rows.
    var existingDefault = repo.findByIsDefaultTrue();
    if (existingDefault.isPresent()) {
      log.info(
          "DIAG-SEEDER-SKIP: default profile already present id={}", existingDefault.get().getId());
      return;
    }
    String policyXml = presetLoader.load(ProfilePreset.STANDARD);
    log.info(
        "DIAG-SEEDER-LOADED: bytes={} head={}",
        policyXml.length(),
        policyXml.substring(0, Math.min(200, policyXml.length())));
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName("STANDARD");
    p.setDescription("DSS default validation policy");
    p.setPreset(ProfilePreset.STANDARD);
    p.setPolicyXml(policyXml);
    p.setIsDefault(true);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    repo.save(p);
    log.info("DIAG-SEEDER-SAVED: id={} policyBytes={}", p.getId(), p.getPolicyXml().length());
  }
}
