package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class VerificationProfileService {

  private final VerificationProfileRepository repo;
  private final PresetXmlLoader presetLoader;
  private final AuditService audit;

  public VerificationProfileService(
      VerificationProfileRepository repo, PresetXmlLoader presetLoader, AuditService audit) {
    this.repo = repo;
    this.presetLoader = presetLoader;
    this.audit = audit;
  }

  public Page<VerificationProfile> findAll(Pageable pageable) {
    return repo.findAll(pageable);
  }

  public VerificationProfile findById(UUID id) {
    return repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
  }

  @Transactional
  public VerificationProfile create(
      String name, String description, ProfilePreset preset, String customXml, Principal actor) {
    if (repo.findByName(name).isPresent()) throw AppException.conflict("profile name taken");
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName(name);
    p.setDescription(description);
    p.setPreset(preset);
    p.setPolicyXml(preset == ProfilePreset.CUSTOM ? customXml : presetLoader.load(preset));
    p.setIsDefault(false);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    VerificationProfile saved = repo.save(p);

    audit.log(
        actor,
        AuditActions.PROFILE_CREATE,
        "verification-profile",
        saved.getId().toString(),
        true,
        Map.of("name", name, "preset", preset.name()));

    return saved;
  }

  @Transactional
  public VerificationProfile update(
      UUID id, String description, String customXml, Principal actor) {
    VerificationProfile p =
        repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    if (description != null) p.setDescription(description);
    if (customXml != null) {
      if (p.getPreset() != ProfilePreset.CUSTOM)
        throw AppException.badRequest("policyXml editing allowed only on CUSTOM");
      p.setPolicyXml(customXml);
    }
    p.setUpdatedAt(Instant.now());
    VerificationProfile saved = repo.save(p);

    // Details intentionally limited: avoid logging the full policy XML. If the field is updated
    // we record that fact; the audit consumer can correlate with the request body via requestId.
    Map<String, Object> details =
        Map.of(
            "descriptionChanged", description != null,
            "policyXmlChanged", customXml != null);

    audit.log(
        actor,
        AuditActions.PROFILE_UPDATE,
        "verification-profile",
        saved.getId().toString(),
        true,
        details);

    return saved;
  }

  @Transactional
  public void delete(UUID id, Principal actor) {
    VerificationProfile p =
        repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    if (p.getIsDefault()) throw AppException.conflict("cannot delete default profile");
    repo.deleteById(id);

    audit.log(
        actor,
        AuditActions.PROFILE_DELETE,
        "verification-profile",
        p.getId().toString(),
        true,
        Map.of("name", p.getName()));
  }

  @Transactional
  public VerificationProfile setDefault(UUID id, Principal actor) {
    VerificationProfile target =
        repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    repo.findByIsDefaultTrue()
        .ifPresent(
            curr -> {
              curr.setIsDefault(false);
              repo.save(curr);
            });
    target.setIsDefault(true);
    target.setUpdatedAt(Instant.now());
    VerificationProfile saved = repo.save(target);

    audit.log(
        actor,
        AuditActions.PROFILE_SET_DEFAULT,
        "verification-profile",
        saved.getId().toString(),
        true,
        null);

    return saved;
  }

  public VerificationProfile getOrDefault(UUID id) {
    if (id != null)
      return repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    return repo.findByIsDefaultTrue()
        .orElseThrow(() -> AppException.notFound("no default profile"));
  }
}
