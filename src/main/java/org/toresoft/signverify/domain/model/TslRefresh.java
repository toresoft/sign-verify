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
@Table(name = "tsl_refresh")
public class TslRefresh {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RefreshTrigger trigger;

  @Enumerated(EnumType.STRING)
  @Column(name = "triggered_by_principal_type", length = 20)
  private PrincipalType triggeredByPrincipalType;

  @Column(name = "triggered_by_principal_id", length = 120)
  private String triggeredByPrincipalId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RefreshStatus status;

  @Column(name = "sources_total", nullable = false)
  private int sourcesTotal;

  @Column(name = "sources_failed", nullable = false)
  private int sourcesFailed;

  @Column(name = "certificates_added", nullable = false)
  private int certificatesAdded;

  @Column(name = "certificates_removed", nullable = false)
  private int certificatesRemoved;

  @Column(name = "certificates_unchanged", nullable = false)
  private int certificatesUnchanged;

  @Column(name = "error_summary", columnDefinition = "TEXT")
  private String errorSummary;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public RefreshTrigger getTrigger() {
    return trigger;
  }

  public void setTrigger(RefreshTrigger t) {
    this.trigger = t;
  }

  public PrincipalType getTriggeredByPrincipalType() {
    return triggeredByPrincipalType;
  }

  public void setTriggeredByPrincipalType(PrincipalType t) {
    this.triggeredByPrincipalType = t;
  }

  public String getTriggeredByPrincipalId() {
    return triggeredByPrincipalId;
  }

  public void setTriggeredByPrincipalId(String s) {
    this.triggeredByPrincipalId = s;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant t) {
    this.startedAt = t;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant t) {
    this.completedAt = t;
  }

  public RefreshStatus getStatus() {
    return status;
  }

  public void setStatus(RefreshStatus s) {
    this.status = s;
  }

  public int getSourcesTotal() {
    return sourcesTotal;
  }

  public void setSourcesTotal(int n) {
    this.sourcesTotal = n;
  }

  public int getSourcesFailed() {
    return sourcesFailed;
  }

  public void setSourcesFailed(int n) {
    this.sourcesFailed = n;
  }

  public int getCertificatesAdded() {
    return certificatesAdded;
  }

  public void setCertificatesAdded(int n) {
    this.certificatesAdded = n;
  }

  public int getCertificatesRemoved() {
    return certificatesRemoved;
  }

  public void setCertificatesRemoved(int n) {
    this.certificatesRemoved = n;
  }

  public int getCertificatesUnchanged() {
    return certificatesUnchanged;
  }

  public void setCertificatesUnchanged(int n) {
    this.certificatesUnchanged = n;
  }

  public String getErrorSummary() {
    return errorSummary;
  }

  public void setErrorSummary(String s) {
    this.errorSummary = s;
  }
}
