package org.toresoft.signverify.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class ApiKeyService {

  public record CreateResult(ApiKey entity, String plaintext) {}

  private static final SecureRandom RND = new SecureRandom();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  private final ApiKeyRepository repo;
  private final PasswordHasherPort hasher;

  public ApiKeyService(ApiKeyRepository repo, PasswordHasherPort hasher) {
    this.repo = repo;
    this.hasher = hasher;
  }

  public Page<ApiKey> findAll(Pageable pageable) {
    return repo.findAll(pageable);
  }

  @Transactional
  public CreateResult create(String name, Role role, Instant expiresAt, Principal actor) {
    if (repo.findByName(name).isPresent()) {
      throw AppException.conflict("name already taken");
    }
    byte[] r = new byte[36];
    RND.nextBytes(r);
    String body = B64.encodeToString(r);
    String prefix = body.substring(0, 8);
    String plaintext = "sv_" + prefix + "_" + body;

    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName(name);
    k.setKeyPrefix(prefix);
    k.setKeyHash(hasher.hash(plaintext));
    k.setRole(role);
    k.setEnabled(true);
    k.setExpiresAt(expiresAt);
    k.setCreatedAt(Instant.now());
    k.setCreatedByPrincipalType(actor.type());
    k.setCreatedByPrincipalId(actor.id());
    repo.save(k);
    return new CreateResult(k, plaintext);
  }

  @Transactional
  public void delete(UUID id, Principal actor) {
    ApiKey k = repo.findById(id).orElseThrow(() -> AppException.notFound("api key not found"));
    enforceLastPrivilegedInvariant(k);
    repo.deleteById(id);
  }

  @Transactional
  public ApiKey patch(UUID id, Boolean enabled, Principal actor) {
    ApiKey k = repo.findById(id).orElseThrow(() -> AppException.notFound("api key not found"));
    if (enabled != null && !enabled && k.isEnabled()) {
      enforceLastPrivilegedInvariant(k);
    }
    if (enabled != null) k.setEnabled(enabled);
    return repo.save(k);
  }

  private void enforceLastPrivilegedInvariant(ApiKey k) {
    if (k.getRole() == Role.PRIVILEGED && k.isEnabled()) {
      long count = repo.countByRoleAndEnabled(Role.PRIVILEGED, true);
      if (count <= 1) {
        throw AppException.conflict("cannot remove last enabled privileged api key");
      }
    }
  }
}
