package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@Service
public class VerificationProfileService {

  private final VerificationProfileRepository repo;
  private final PresetXmlLoader presetLoader;

  public VerificationProfileService(
      VerificationProfileRepository repo, PresetXmlLoader presetLoader) {
    this.repo = repo;
    this.presetLoader = presetLoader;
  }

  public Page<VerificationProfile> findAll(Pageable pageable) {
    return repo.findAll(pageable);
  }

  public VerificationProfile findById(UUID id) {
    return repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
  }

  @Transactional
  public VerificationProfile create(
      String name, String description, ProfilePreset preset, String customXml) {
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
    return repo.save(p);
  }

  @Transactional
  public VerificationProfile update(UUID id, String description, String customXml) {
    VerificationProfile p =
        repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    if (description != null) p.setDescription(description);
    if (customXml != null) {
      if (p.getPreset() != ProfilePreset.CUSTOM)
        throw AppException.badRequest("policyXml editing allowed only on CUSTOM");
      p.setPolicyXml(customXml);
    }
    p.setUpdatedAt(Instant.now());
    return repo.save(p);
  }

  @Transactional
  public void delete(UUID id) {
    VerificationProfile p =
        repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    if (p.getIsDefault()) throw AppException.conflict("cannot delete default profile");
    repo.deleteById(id);
  }

  @Transactional
  public VerificationProfile setDefault(UUID id) {
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
    return repo.save(target);
  }

  public VerificationProfile getOrDefault(UUID id) {
    if (id != null)
      return repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    return repo.findByIsDefaultTrue()
        .orElseThrow(() -> AppException.notFound("no default profile"));
  }
}
