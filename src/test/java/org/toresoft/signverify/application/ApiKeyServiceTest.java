package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  private final AuditService audit = mock(AuditService.class);
  private ApiKeyService service;

  private final Principal admin =
      new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.reset(repo, hasher, audit);
    service = new ApiKeyService(repo, hasher, audit);
  }

  @Test
  void delete_last_privileged_throws_conflict_andAuditsBlocked() {
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
    when(repo.lockEnabledIdsByRole(Role.PRIVILEGED)).thenReturn(List.of(id));

    assertThatThrownBy(() -> service.delete(id, admin))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Conflict");
    verify(repo, never()).deleteById(any());

    // The block is recorded as a failed audit event.
    verify(audit, times(1))
        .log(
            eq(admin),
            eq(AuditActions.APIKEY_LAST_PRIVILEGED_BLOCKED),
            eq("api-key"),
            eq(id.toString()),
            eq(false),
            isNull());
  }

  @Test
  void delete_when_other_privileged_exists_auditsSuccess() {
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
    when(repo.lockEnabledIdsByRole(Role.PRIVILEGED)).thenReturn(List.of(id, UUID.randomUUID()));

    service.delete(id, admin);
    verify(repo).deleteById(id);

    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit, times(1))
        .log(
            eq(admin),
            eq(AuditActions.APIKEY_DELETE),
            eq("api-key"),
            eq(id.toString()),
            eq(true),
            details.capture());
    assertThat(details.getValue()).containsEntry("name", "k");
  }

  @Test
  void create_returns_plaintext_once_andAuditsCreate() {
    when(hasher.hash(any())).thenReturn("$2a$hash");
    when(repo.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.create("alice", Role.STANDARD, null, admin);
    assertThat(res.plaintext()).startsWith("sv_");
    assertThat(res.entity().getKeyHash()).isEqualTo("$2a$hash");

    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit, times(1))
        .log(
            eq(admin),
            eq(AuditActions.APIKEY_CREATE),
            eq("api-key"),
            anyString(),
            eq(true),
            details.capture());
    assertThat(details.getValue()).containsEntry("name", "alice").containsEntry("role", "STANDARD");
  }

  @Test
  void patch_disablesKey_auditsUpdate() {
    UUID id = UUID.randomUUID();
    ApiKey k = new ApiKey();
    k.setId(id);
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setName("svc");
    k.setKeyPrefix("svc00001");
    k.setKeyHash("h");
    k.setCreatedAt(Instant.now());
    when(repo.findById(id)).thenReturn(Optional.of(k));
    when(repo.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

    service.patch(id, Boolean.FALSE, admin);

    ArgumentCaptor<Map<String, Object>> details = ArgumentCaptor.forClass(Map.class);
    verify(audit, times(1))
        .log(
            eq(admin),
            eq(AuditActions.APIKEY_UPDATE),
            eq("api-key"),
            eq(id.toString()),
            eq(true),
            details.capture());
    assertThat(details.getValue()).containsEntry("enabled", false);
  }
}
