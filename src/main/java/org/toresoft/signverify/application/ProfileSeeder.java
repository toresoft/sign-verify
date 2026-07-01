/**
 * sign-verify Copyright (C) 2026 toresoft
 *
 * <p>This file is part of the "sign-verify" project.
 *
 * <p>This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301 USA
 */
package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
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

  /**
   * Ensures a real DSS STANDARD validation policy is the active default.
   *
   * <p>Must run in a transaction because some ITs (e.g. AuditLogIT) save custom profiles with
   * isDefault=true and stub XML like {@code <policy/>}. The verification_profile table has no
   * UNIQUE constraint on is_default, so multiple default rows can coexist and {@code
   * findByIsDefaultTrue()} can return the stub instead of the real DSS policy, which downstream
   * causes DSS to reject the document with HTTP 400 "invalid validation policy".
   *
   * <p>Strategy: if no default profile exists, create one with the canonical STANDARD policy. If
   * one already exists but its policy XML is not byte-identical to the canonical XML loaded from
   * classpath, overwrite it (also reset name/description/preset when the row is clearly a test
   * stub, e.g. name starts with "default-"). This restores the canonical default and leaves any
   * genuine CUSTOM default profile untouched: those have preset=CUSTOM and never carry
   * isDefault=true from this seeder.
   */
  @EventListener
  @Transactional
  public void onReady(ApplicationReadyEvent ev) {
    seedStandardDefault();
    seedPreset(
        "agid", "Firma digitale italiana (AgID) — solo firme qualificate", ProfilePreset.AGID);
    seedPreset(
        "agid-ts",
        "Firma digitale italiana (AgID) — solo firme qualificate con marca temporale",
        ProfilePreset.AGID_TS);
  }

  private void seedStandardDefault() {
    String policyXml = presetLoader.load(ProfilePreset.STANDARD);
    var existing = repo.findByIsDefaultTrue();
    if (existing.isPresent()) {
      VerificationProfile p = existing.get();
      if (isCanonical(p, policyXml)) {
        return;
      }
      p.setPolicyXml(policyXml);
      p.setPreset(ProfilePreset.STANDARD);
      if (p.getName() == null || p.getName().startsWith("default-")) {
        p.setName("STANDARD");
        p.setDescription("DSS default validation policy");
      }
      p.setUpdatedAt(Instant.now());
      repo.save(p);
      return;
    }
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
  }

  /**
   * Preloads a non-default, ready-to-use profile for the given preset, addressed by {@code name}.
   * Idempotent: skips creation when a profile with that name already exists, so an operator can
   * later customise or delete it without the seeder resurrecting it on the next boot. Never the
   * system default; {@link #seedStandardDefault()} owns that.
   */
  private void seedPreset(String name, String description, ProfilePreset preset) {
    if (repo.findByName(name).isPresent()) {
      return;
    }
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName(name);
    p.setDescription(description);
    p.setPreset(preset);
    p.setPolicyXml(presetLoader.load(preset));
    p.setIsDefault(false);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    repo.save(p);
  }

  /**
   * Returns true if the given profile is already the canonical STANDARD profile seeded by this
   * seeder, i.e. its policy XML is byte-for-byte the same as the file on the classpath and its
   * preset is STANDARD.
   */
  private static boolean isCanonical(VerificationProfile p, String canonicalPolicyXml) {
    String xml = p.getPolicyXml();
    if (xml == null || !xml.equals(canonicalPolicyXml)) return false;
    return ProfilePreset.STANDARD.equals(p.getPreset());
  }
}
