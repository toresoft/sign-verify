package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.persistence.AuditLogRepository;
import org.toresoft.signverify.security.Principal;

class AuditServiceTest {

  private final AuditLogRepository repo = mock(AuditLogRepository.class);
  private final ObjectMapper om = new ObjectMapper();
  private final AuditService service = new AuditService(repo, om);

  @BeforeEach
  void resetMocks() {
    org.mockito.Mockito.reset(repo);
  }

  @Test
  void log_withActor_setsPrincipalFromActor() {
    Principal actor = new Principal(PrincipalType.API_KEY, "user-42", Role.PRIVILEGED, "u");

    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getPrincipalType()).isEqualTo(PrincipalType.API_KEY);
    assertThat(saved.getPrincipalId()).isEqualTo("user-42");
  }

  @Test
  void log_withNullActor_setsSystemPrincipal() {
    service.log(null, "system-task", "tsl", "eu", true, null);

    AuditLog saved = capture();
    assertThat(saved.getPrincipalType()).isEqualTo(PrincipalType.SYSTEM);
    assertThat(saved.getPrincipalId()).isEqualTo("system");
  }

  @Test
  void log_setsActionAndTarget() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");

    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getAction()).isEqualTo("create");
    assertThat(saved.getTargetType()).isEqualTo("api-key");
    assertThat(saved.getTargetId()).isEqualTo("abc");
  }

  @Test
  void log_setsSuccessTrue() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");

    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.isSuccess()).isTrue();
  }

  @Test
  void log_setsSuccessFalse() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");

    service.log(actor, "delete", "api-key", "abc", false, null);

    AuditLog saved = capture();
    assertThat(saved.isSuccess()).isFalse();
  }

  @Test
  void log_serializesDetailsAsJson() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");
    Map<String, Object> details = Map.of("reason", "expired", "count", 3);

    service.log(actor, "delete", "api-key", "abc", true, details);

    AuditLog saved = capture();
    assertThat(saved.getDetails()).isNotNull();
    assertThat(saved.getDetails()).contains("\"reason\"", "expired", "\"count\"", "3");
  }

  @Test
  void log_nullDetails_storesNull() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");

    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getDetails()).isNull();
  }

  @Test
  void log_generatesUuidAndTimestamp() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");
    Instant before = Instant.now();

    service.log(actor, "create", "api-key", "abc", true, null);

    Instant after = Instant.now();
    AuditLog saved = capture();
    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getOccurredAt()).isNotNull();
    assertThat(saved.getOccurredAt()).isBetween(before, after);
  }

  @Test
  void log_swallowsSerializationExceptions() {
    Principal actor = new Principal(PrincipalType.API_KEY, "u", Role.PRIVILEGED, "u");
    Object unserializable = new ThrowingObject();

    service.log(actor, "create", "api-key", "abc", true, Map.of("bad", unserializable));

    AuditLog saved = capture();
    // service should still save; details is null because serialization was swallowed
    assertThat(saved.getDetails()).isNull();
    assertThat(saved.getAction()).isEqualTo("create");
  }

  private AuditLog capture() {
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(repo).save(captor.capture());
    return captor.getValue();
  }

  /** A class Jackson cannot serialize, to trigger JsonProcessingException. */
  static class ThrowingObject {
    @com.fasterxml.jackson.annotation.JsonValue
    public Object fail() {
      throw new RuntimeException("boom");
    }
  }
}
