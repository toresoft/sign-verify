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
@Table(name = "audit_log")
public class AuditLog {

  @Id private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "principal_type", nullable = false, length = 20)
  private PrincipalType principalType;

  @Column(name = "principal_id", nullable = false, length = 120)
  private String principalId;

  @Column(nullable = false, length = 60)
  private String action;

  @Column(name = "target_type", length = 40)
  private String targetType;

  @Column(name = "target_id", length = 120)
  private String targetId;

  @Column(nullable = false)
  private boolean success;

  @Column(columnDefinition = "TEXT")
  private String details;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant t) {
    this.occurredAt = t;
  }

  public PrincipalType getPrincipalType() {
    return principalType;
  }

  public void setPrincipalType(PrincipalType t) {
    this.principalType = t;
  }

  public String getPrincipalId() {
    return principalId;
  }

  public void setPrincipalId(String s) {
    this.principalId = s;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String s) {
    this.action = s;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String s) {
    this.targetType = s;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String s) {
    this.targetId = s;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean b) {
    this.success = b;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String s) {
    this.details = s;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String s) {
    this.ipAddress = s;
  }
}
