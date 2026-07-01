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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

class BootstrapApiKeyGeneratorTest {

  @Test
  void generates_and_writes_file_when_no_privileged(@TempDir Path tmp) {
    ApiKeyRepository repo = mock(ApiKeyRepository.class);
    PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(0L);
    when(hasher.hash(any())).thenReturn("$2a$12$hashed");

    Path keyFile = tmp.resolve("bootstrap.txt");
    BootstrapApiKeyGenerator gen = new BootstrapApiKeyGenerator(repo, hasher, keyFile.toString());

    gen.onReady(mock(ApplicationReadyEvent.class));

    verify(repo).save(any(ApiKey.class));
    assertThat(keyFile).exists();
    String contents = readFile(keyFile);
    assertThat(contents).startsWith("sv_");
  }

  @Test
  void skips_when_privileged_already_exists(@TempDir Path tmp) {
    ApiKeyRepository repo = mock(ApiKeyRepository.class);
    PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(1L);

    Path keyFile = tmp.resolve("bootstrap.txt");
    BootstrapApiKeyGenerator gen = new BootstrapApiKeyGenerator(repo, hasher, keyFile.toString());
    gen.onReady(mock(ApplicationReadyEvent.class));

    verify(repo, never()).save(any());
    assertThat(keyFile).doesNotExist();
  }

  private String readFile(Path p) {
    try {
      return Files.readString(p);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
