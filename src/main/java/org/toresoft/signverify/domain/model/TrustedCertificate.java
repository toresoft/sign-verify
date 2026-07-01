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
@Table(name = "trusted_certificate")
public class TrustedCertificate {

  @Id private UUID id;

  @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 64)
  private String fingerprintSha256;

  @Column(length = 64)
  private String ski;

  @Column(length = 64)
  private String aki;

  @Column(name = "subject_dn", columnDefinition = "TEXT")
  private String subjectDn;

  @Column(name = "subject_cn", length = 255)
  private String subjectCn;

  @Column(name = "issuer_dn", columnDefinition = "TEXT")
  private String issuerDn;

  @Column(name = "issuer_cn", length = 255)
  private String issuerCn;

  @Column(name = "serial_number", length = 80)
  private String serialNumber;

  @Column(length = 8)
  private String country;

  @Column(name = "tsp_name", length = 255)
  private String tspName;

  @Column(name = "tsp_service_type", length = 255)
  private String tspServiceType;

  @Column(name = "tsp_service_status", length = 80)
  private String tspServiceStatus;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_to")
  private Instant validTo;

  @Column(name = "certificate_der_b64", nullable = false, columnDefinition = "TEXT")
  private String certificateDerB64;

  @Column(name = "tsl_url", columnDefinition = "TEXT")
  private String tslUrl;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "removed_at")
  private Instant removedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getFingerprintSha256() {
    return fingerprintSha256;
  }

  public void setFingerprintSha256(String s) {
    this.fingerprintSha256 = s;
  }

  public String getSki() {
    return ski;
  }

  public void setSki(String s) {
    this.ski = s;
  }

  public String getAki() {
    return aki;
  }

  public void setAki(String s) {
    this.aki = s;
  }

  public String getSubjectDn() {
    return subjectDn;
  }

  public void setSubjectDn(String s) {
    this.subjectDn = s;
  }

  public String getSubjectCn() {
    return subjectCn;
  }

  public void setSubjectCn(String s) {
    this.subjectCn = s;
  }

  public String getIssuerDn() {
    return issuerDn;
  }

  public void setIssuerDn(String s) {
    this.issuerDn = s;
  }

  public String getIssuerCn() {
    return issuerCn;
  }

  public void setIssuerCn(String s) {
    this.issuerCn = s;
  }

  public String getSerialNumber() {
    return serialNumber;
  }

  public void setSerialNumber(String s) {
    this.serialNumber = s;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String s) {
    this.country = s;
  }

  public String getTspName() {
    return tspName;
  }

  public void setTspName(String s) {
    this.tspName = s;
  }

  public String getTspServiceType() {
    return tspServiceType;
  }

  public void setTspServiceType(String s) {
    this.tspServiceType = s;
  }

  public String getTspServiceStatus() {
    return tspServiceStatus;
  }

  public void setTspServiceStatus(String s) {
    this.tspServiceStatus = s;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Instant t) {
    this.validFrom = t;
  }

  public Instant getValidTo() {
    return validTo;
  }

  public void setValidTo(Instant t) {
    this.validTo = t;
  }

  public String getCertificateDerB64() {
    return certificateDerB64;
  }

  public void setCertificateDerB64(String s) {
    this.certificateDerB64 = s;
  }

  public String getTslUrl() {
    return tslUrl;
  }

  public void setTslUrl(String s) {
    this.tslUrl = s;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant t) {
    this.lastSeenAt = t;
  }

  public Instant getRemovedAt() {
    return removedAt;
  }

  public void setRemovedAt(Instant t) {
    this.removedAt = t;
  }
}
