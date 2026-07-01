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
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_profile")
public class VerificationProfile {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProfilePreset preset;

  @Column(name = "policy_xml", nullable = false, columnDefinition = "TEXT")
  private String policyXml;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Version private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String d) {
    this.description = d;
  }

  public ProfilePreset getPreset() {
    return preset;
  }

  public void setPreset(ProfilePreset preset) {
    this.preset = preset;
  }

  public String getPolicyXml() {
    return policyXml;
  }

  public void setPolicyXml(String xml) {
    this.policyXml = xml;
  }

  public boolean getIsDefault() {
    return isDefault;
  }

  public void setIsDefault(boolean d) {
    this.isDefault = d;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long v) {
    this.version = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant t) {
    this.createdAt = t;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant t) {
    this.updatedAt = t;
  }
}
