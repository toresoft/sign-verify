package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKey {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Column(name = "key_prefix", nullable = false, length = 8)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, length = 255)
  private String keyHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private boolean bootstrap;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "created_by_principal_type", length = 20)
  private PrincipalType createdByPrincipalType;

  @Column(name = "created_by_principal_id", length = 120)
  private String createdByPrincipalId;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

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

  public String getKeyPrefix() {
    return keyPrefix;
  }

  public void setKeyPrefix(String keyPrefix) {
    this.keyPrefix = keyPrefix;
  }

  public String getKeyHash() {
    return keyHash;
  }

  public void setKeyHash(String keyHash) {
    this.keyHash = keyHash;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isBootstrap() {
    return bootstrap;
  }

  public void setBootstrap(boolean bootstrap) {
    this.bootstrap = bootstrap;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public PrincipalType getCreatedByPrincipalType() {
    return createdByPrincipalType;
  }

  public void setCreatedByPrincipalType(PrincipalType t) {
    this.createdByPrincipalType = t;
  }

  public String getCreatedByPrincipalId() {
    return createdByPrincipalId;
  }

  public void setCreatedByPrincipalId(String id) {
    this.createdByPrincipalId = id;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
