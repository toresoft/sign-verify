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
@Table(name = "validation_job")
public class ValidationJob {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JobStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "original_status", length = 20)
  private JobStatus originalStatus;

  @Column(name = "profile_id")
  private UUID profileId;

  @Column(name = "profile_overrides", columnDefinition = "TEXT")
  private String profileOverrides;

  @Column(name = "reports_requested", nullable = false, length = 100)
  private String reportsRequested;

  @Column(name = "document_path", length = 500)
  private String documentPath;

  @Column(name = "document_filename", length = 255)
  private String documentFilename;

  @Column(name = "result_path", length = 500)
  private String resultPath;

  @Column(name = "callback_url", length = 500)
  private String callbackUrl;

  @Column(name = "callback_secret_cipher", columnDefinition = "TEXT")
  private String callbackSecretCipher;

  @Column(name = "callback_algorithm", length = 20)
  private String callbackAlgorithm;

  @Column(name = "callback_attempts", nullable = false)
  private int callbackAttempts;

  @Column(name = "next_callback_at")
  private Instant nextCallbackAt;

  @Column(name = "last_callback_error", columnDefinition = "TEXT")
  private String lastCallbackError;

  @Column(name = "pickup_attempts", nullable = false)
  private int pickupAttempts;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "requested_by_principal_type", nullable = false, length = 20)
  private PrincipalType requestedByPrincipalType;

  @Column(name = "requested_by_principal_id", nullable = false, length = 120)
  private String requestedByPrincipalId;

  @Column(name = "last_accessed_at")
  private Instant lastAccessedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public JobStatus getStatus() {
    return status;
  }

  public void setStatus(JobStatus s) {
    this.status = s;
  }

  public JobStatus getOriginalStatus() {
    return originalStatus;
  }

  public void setOriginalStatus(JobStatus s) {
    this.originalStatus = s;
  }

  public UUID getProfileId() {
    return profileId;
  }

  public void setProfileId(UUID id) {
    this.profileId = id;
  }

  public String getProfileOverrides() {
    return profileOverrides;
  }

  public void setProfileOverrides(String s) {
    this.profileOverrides = s;
  }

  public String getReportsRequested() {
    return reportsRequested;
  }

  public void setReportsRequested(String s) {
    this.reportsRequested = s;
  }

  public String getDocumentPath() {
    return documentPath;
  }

  public void setDocumentPath(String s) {
    this.documentPath = s;
  }

  public String getDocumentFilename() {
    return documentFilename;
  }

  public void setDocumentFilename(String s) {
    this.documentFilename = s;
  }

  public String getResultPath() {
    return resultPath;
  }

  public void setResultPath(String s) {
    this.resultPath = s;
  }

  public String getCallbackUrl() {
    return callbackUrl;
  }

  public void setCallbackUrl(String s) {
    this.callbackUrl = s;
  }

  public String getCallbackSecretCipher() {
    return callbackSecretCipher;
  }

  public void setCallbackSecretCipher(String s) {
    this.callbackSecretCipher = s;
  }

  public String getCallbackAlgorithm() {
    return callbackAlgorithm;
  }

  public void setCallbackAlgorithm(String s) {
    this.callbackAlgorithm = s;
  }

  public int getCallbackAttempts() {
    return callbackAttempts;
  }

  public void setCallbackAttempts(int n) {
    this.callbackAttempts = n;
  }

  public Instant getNextCallbackAt() {
    return nextCallbackAt;
  }

  public void setNextCallbackAt(Instant t) {
    this.nextCallbackAt = t;
  }

  public String getLastCallbackError() {
    return lastCallbackError;
  }

  public void setLastCallbackError(String s) {
    this.lastCallbackError = s;
  }

  public int getPickupAttempts() {
    return pickupAttempts;
  }

  public void setPickupAttempts(int n) {
    this.pickupAttempts = n;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant t) {
    this.createdAt = t;
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

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Instant t) {
    this.deliveredAt = t;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant t) {
    this.expiresAt = t;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant t) {
    this.deletedAt = t;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String s) {
    this.errorMessage = s;
  }

  public PrincipalType getRequestedByPrincipalType() {
    return requestedByPrincipalType;
  }

  public void setRequestedByPrincipalType(PrincipalType t) {
    this.requestedByPrincipalType = t;
  }

  public String getRequestedByPrincipalId() {
    return requestedByPrincipalId;
  }

  public void setRequestedByPrincipalId(String s) {
    this.requestedByPrincipalId = s;
  }

  public Instant getLastAccessedAt() {
    return lastAccessedAt;
  }

  public void setLastAccessedAt(Instant t) {
    this.lastAccessedAt = t;
  }
}
