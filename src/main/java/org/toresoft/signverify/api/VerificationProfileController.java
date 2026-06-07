package org.toresoft.signverify.api;

import java.time.ZoneOffset;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.api.dto.*;
import org.toresoft.signverify.api.spi.ProfilesApi;
import org.toresoft.signverify.application.VerificationProfileService;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;

@RestController
public class VerificationProfileController implements ProfilesApi {

  private final VerificationProfileService service;

  public VerificationProfileController(VerificationProfileService service) {
    this.service = service;
  }

  @Override
  public ResponseEntity<ProfilePage> listProfiles(Integer page, Integer size) {
    var p = service.findAll(PageRequest.of(page == null ? 0 : page, size == null ? 20 : size));
    ProfilePage out = new ProfilePage();
    out.setPage(p.getNumber());
    out.setSize(p.getSize());
    out.setTotalElements(p.getTotalElements());
    out.setTotalPages(p.getTotalPages());
    out.setContent(p.map(this::toView).toList());
    return ResponseEntity.ok(out);
  }

  @Override
  public ResponseEntity<ProfileView> getProfile(UUID id) {
    return ResponseEntity.ok(toView(service.findById(id)));
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<ProfileView> createProfile(ProfileCreateRequest req) {
    VerificationProfile p =
        service.create(
            req.getName(),
            req.getDescription() != null && req.getDescription().isPresent()
                ? req.getDescription().get()
                : null,
            ProfilePreset.valueOf(req.getPreset().getValue()),
            req.getPolicyXml() != null && req.getPolicyXml().isPresent()
                ? req.getPolicyXml().get()
                : null);
    return ResponseEntity.status(201).body(toView(p));
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<ProfileView> updateProfile(UUID id, ProfileUpdateRequest req) {
    String desc =
        req.getDescription() != null && req.getDescription().isPresent()
            ? req.getDescription().get()
            : null;
    String xml =
        req.getPolicyXml() != null && req.getPolicyXml().isPresent()
            ? req.getPolicyXml().get()
            : null;
    return ResponseEntity.ok(toView(service.update(id, desc, xml)));
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<Void> deleteProfile(UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<ProfileView> setDefaultProfile(UUID id) {
    return ResponseEntity.ok(toView(service.setDefault(id)));
  }

  private ProfileView toView(VerificationProfile p) {
    ProfileView v = new ProfileView();
    v.setId(p.getId());
    v.setName(p.getName());
    v.setDescription(
        p.getDescription() != null
            ? JsonNullable.of(p.getDescription())
            : JsonNullable.<String>undefined());
    v.setPreset(ProfileView.PresetEnum.valueOf(p.getPreset().name()));
    v.setIsDefault(p.getIsDefault());
    v.setCreatedAt(p.getCreatedAt().atOffset(ZoneOffset.UTC));
    v.setUpdatedAt(p.getUpdatedAt().atOffset(ZoneOffset.UTC));
    return v;
  }
}
