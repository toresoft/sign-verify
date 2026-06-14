package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.toresoft.signverify.config.AuditProperties;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.persistence.AuditLogRepository;
import org.toresoft.signverify.security.Principal;

class AuditServiceTest {

  private final AuditLogRepository repo = mock(AuditLogRepository.class);
  private final ObjectMapper om = new ObjectMapper();
  private final AuditProperties props = new AuditProperties();
  private final AuditService service = new AuditService(repo, om, props);

  private final Principal actor =
      new Principal(PrincipalType.API_KEY, "user-42", Role.PRIVILEGED, "u");

  @BeforeEach
  void resetMocks() throws Exception {
    org.mockito.Mockito.reset(repo);
    MDC.clear();
    // In unit tests the Spring AOP proxy is not present. The @Lazy self-injection field
    // (AuditService.self) is null, so writeAsync is called directly on the same instance —
    // effectively bypassing @Async and @Transactional. This is correct for unit testing because
    // the async/transactional behavior is verified in integration tests (AuditLogIT).
    // Set self to the service itself so that the synchronous log() → self.writeAsync() call
    // works without a NullPointerException.
    Field selfField = AuditService.class.getDeclaredField("self");
    selfField.setAccessible(true);
    selfField.set(service, service);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void log_withActor_setsPrincipalFromActor() {
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
    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getAction()).isEqualTo("create");
    assertThat(saved.getTargetType()).isEqualTo("api-key");
    assertThat(saved.getTargetId()).isEqualTo("abc");
  }

  @Test
  void log_setsSuccessTrue() {
    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.isSuccess()).isTrue();
  }

  @Test
  void log_setsSuccessFalse() {
    service.log(actor, "delete", "api-key", "abc", false, null);

    AuditLog saved = capture();
    assertThat(saved.isSuccess()).isFalse();
  }

  @Test
  void log_serializesDetailsAsJson() {
    Map<String, Object> details = Map.of("reason", "expired", "count", 3);

    service.log(actor, "delete", "api-key", "abc", true, details);

    AuditLog saved = capture();
    assertThat(saved.getDetails()).isNotNull();
    assertThat(saved.getDetails()).contains("\"reason\"", "expired", "\"count\"", "3");
  }

  @Test
  void log_nullDetails_storesNull() {
    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getDetails()).isNull();
  }

  @Test
  void log_generatesUuidAndTimestamp() {
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
    Object unserializable = new ThrowingObject();

    service.log(actor, "create", "api-key", "abc", true, Map.of("bad", unserializable));

    AuditLog saved = capture();
    assertThat(saved.getDetails()).isNull();
    assertThat(saved.getAction()).isEqualTo("create");
  }

  @Test
  void log_disabled_skipsSave() {
    props.setEnabled(false);

    service.log(actor, "create", "api-key", "abc", true, null);

    verify(repo, never()).save(any());
  }

  @Test
  void log_readsIpFromMdc() {
    MDC.put("clientIp", "203.0.113.7");

    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
  }

  @Test
  void log_includesRequestIdInDetailsWhenPresent() {
    MDC.put("clientIp", "203.0.113.7");
    MDC.put("requestId", "req-abc-123");

    service.log(actor, "create", "api-key", "abc", true, null);

    AuditLog saved = capture();
    assertThat(saved.getDetails()).contains("requestId").contains("req-abc-123");
  }

  @Test
  void log_overloadWithPrincipalType_setsSystem() {
    service.log(PrincipalType.SYSTEM, "scheduler-1", "tsl.refresh", "tsl", "eu", true, null);

    AuditLog saved = capture();
    assertThat(saved.getPrincipalType()).isEqualTo(PrincipalType.SYSTEM);
    assertThat(saved.getPrincipalId()).isEqualTo("scheduler-1");
  }

  @Test
  void log_repoThrows_doesNotPropagate() {
    when(repo.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

    // Must not throw — audit is best-effort.
    service.log(actor, "create", "api-key", "abc", true, null);
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
