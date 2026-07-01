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
package org.toresoft.signverify.security;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@Component
public class BootstrapApiKeyGenerator {

  private static final Logger log = LoggerFactory.getLogger(BootstrapApiKeyGenerator.class);
  private static final SecureRandom RND = new SecureRandom();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  private final ApiKeyRepository repo;
  private final PasswordHasherPort hasher;
  private final String keyFilePath;

  public BootstrapApiKeyGenerator(
      ApiKeyRepository repo,
      PasswordHasherPort hasher,
      @Value("${app.security.bootstrap-key-file}") String keyFilePath) {
    this.repo = repo;
    this.hasher = hasher;
    this.keyFilePath = keyFilePath;
  }

  @EventListener
  public void onReady(ApplicationReadyEvent event) {
    if (repo.countByRoleAndEnabled(Role.PRIVILEGED, true) > 0) return;

    byte[] random = new byte[36];
    RND.nextBytes(random);
    String body = B64.encodeToString(random);
    String prefix = body.substring(0, 8);
    String plaintext = "sv_" + prefix + "_" + body;

    ApiKey key = new ApiKey();
    key.setId(UUID.randomUUID());
    key.setName("bootstrap-" + Instant.now().getEpochSecond());
    key.setKeyPrefix(prefix);
    key.setKeyHash(hasher.hash(plaintext));
    key.setRole(Role.PRIVILEGED);
    key.setEnabled(true);
    key.setBootstrap(true);
    key.setCreatedAt(Instant.now());
    repo.save(key);

    writeBootstrapFile(plaintext);
    log.warn("BOOTSTRAP API KEY generated. File: {} — delete after pickup.", keyFilePath);
  }

  private void writeBootstrapFile(String plaintext) {
    try {
      Path p = Path.of(keyFilePath);
      Files.createDirectories(p.getParent() != null ? p.getParent() : Path.of("."));
      Files.writeString(
          p, plaintext + "\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.setPosixFilePermissions(
            p, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException ignored) {
        /* Windows */
      }
    } catch (Exception e) {
      throw new IllegalStateException("Cannot write bootstrap key file: " + keyFilePath, e);
    }
  }
}
