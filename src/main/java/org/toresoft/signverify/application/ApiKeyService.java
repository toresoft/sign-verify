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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ApiKey;
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
  private final AuditService audit;

  public ApiKeyService(ApiKeyRepository repo, PasswordHasherPort hasher, AuditService audit) {
    this.repo = repo;
    this.hasher = hasher;
    this.audit = audit;
  }

  public Page<ApiKey> findAll(Pageable pageable) {
    return repo.findAll(pageable);
  }

  @Transactional
  public CreateResult create(String name, Role role, Instant expiresAt, Principal actor) {
    if (repo.findByName(name).isPresent()) {
      throw AppException.conflict("name already taken");
    }

    String body;
    String prefix;
    // Regenerate on the (astronomically rare) prefix collision so the unique constraint never
    // surfaces as a 500 to the caller.
    do {
      byte[] r = new byte[36];
      RND.nextBytes(r);
      body = B64.encodeToString(r);
      prefix = body.substring(0, 8);
    } while (repo.findByKeyPrefix(prefix).isPresent());
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

    // Audit the successful creation. The plaintext key is intentionally NOT included in details.
    audit.log(
        actor,
        AuditActions.APIKEY_CREATE,
        "api-key",
        k.getId().toString(),
        true,
        Map.of("name", name, "role", role.name()));

    return new CreateResult(k, plaintext);
  }

  @Transactional
  public void delete(UUID id, Principal actor) {
    ApiKey k = repo.findById(id).orElseThrow(() -> AppException.notFound("api key not found"));
    enforceLastPrivilegedInvariant(k, actor);
    repo.deleteById(id);

    audit.log(
        actor,
        AuditActions.APIKEY_DELETE,
        "api-key",
        k.getId().toString(),
        true,
        Map.of("name", k.getName()));
  }

  @Transactional
  public ApiKey patch(UUID id, Boolean enabled, Principal actor) {
    ApiKey k = repo.findById(id).orElseThrow(() -> AppException.notFound("api key not found"));
    if (enabled != null && !enabled && k.isEnabled()) {
      enforceLastPrivilegedInvariant(k, actor);
    }
    if (enabled != null) k.setEnabled(enabled);
    ApiKey saved = repo.save(k);

    // Audit the patch. The enabled flag is the only mutable field exposed today; if more are
    // added later, extend details accordingly (and add an explicit @Column whitelist here).
    audit.log(
        actor,
        AuditActions.APIKEY_UPDATE,
        "api-key",
        saved.getId().toString(),
        true,
        Map.of("enabled", saved.isEnabled()));

    return saved;
  }

  private void enforceLastPrivilegedInvariant(ApiKey k, Principal actor) {
    if (k.getRole() == Role.PRIVILEGED && k.isEnabled()) {
      // Pessimistic lock serializes concurrent removals, avoiding a TOCTOU race that could leave
      // zero enabled privileged keys (lock-out).
      long count = repo.lockEnabledIdsByRole(Role.PRIVILEGED).size();
      if (count <= 1) {
        // Record the block as a failed audit event so security operators can see the attempt.
        // The exception that follows is the user-facing error.
        audit.log(
            actor,
            AuditActions.APIKEY_LAST_PRIVILEGED_BLOCKED,
            "api-key",
            k.getId().toString(),
            false,
            null);
        throw AppException.conflict("cannot remove last enabled privileged api key");
      }
    }
  }
}
