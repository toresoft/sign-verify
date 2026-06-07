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
