package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.security.Principal;

class ApiKeyServiceTest {

  private final ApiKeyRepository repo = mock(ApiKeyRepository.class);
  private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
  private final ApiKeyService service = new ApiKeyService(repo, hasher);

  private final Principal admin =
      new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  @Test
  void delete_last_privileged_throws_conflict() {
    UUID id = UUID.randomUUID();
    ApiKey only = new ApiKey();
    only.setId(id);
    only.setRole(Role.PRIVILEGED);
    only.setEnabled(true);
    only.setName("only");
    only.setKeyPrefix("only0001");
    only.setKeyHash("h");
    only.setCreatedAt(Instant.now());
    when(repo.findById(id)).thenReturn(Optional.of(only));
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(1L);

    assertThatThrownBy(() -> service.delete(id, admin))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Conflict");
    verify(repo, never()).deleteById(any());
  }

  @Test
  void delete_when_other_privileged_exists_ok() {
    UUID id = UUID.randomUUID();
    ApiKey k = new ApiKey();
    k.setId(id);
    k.setRole(Role.PRIVILEGED);
    k.setEnabled(true);
    k.setName("k");
    k.setKeyPrefix("priv0001");
    k.setKeyHash("h");
    k.setCreatedAt(Instant.now());
    when(repo.findById(id)).thenReturn(Optional.of(k));
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(2L);

    service.delete(id, admin);
    verify(repo).deleteById(id);
  }

  @Test
  void create_returns_plaintext_once() {
    when(hasher.hash(any())).thenReturn("$2a$hash");
    when(repo.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.create("alice", Role.STANDARD, null, admin);
    assertThat(res.plaintext()).startsWith("sv_");
    assertThat(res.entity().getKeyHash()).isEqualTo("$2a$hash");
  }
}
