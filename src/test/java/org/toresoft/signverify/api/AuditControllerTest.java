package org.toresoft.signverify.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.persistence.AuditLogRepository;

/**
 * Unit tests for {@link AuditController} (FASE 11, Task 11.2). These tests use {@link WebMvcTest}
 * to load only the MVC slice and mock {@link AuditLogRepository}. They are expected to FAIL until
 * {@code AuditController} is implemented.
 */
@WebMvcTest(controllers = AuditController.class)
@org.springframework.context.annotation.Import(AuditControllerTest.PermissiveSecurity.class)
class AuditControllerTest {

  static class PermissiveSecurity {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
      http.csrf(c -> c.disable())
          .authorizeHttpRequests(a -> a.anyRequest().permitAll())
          .httpBasic(b -> b.disable())
          .formLogin(f -> f.disable());
      return http.build();
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockBean private AuditLogRepository repo;
  @MockBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;
  @MockBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  private final AuditLog audit1 =
      baseLog(Instant.parse("2024-01-01T10:00:00Z"), "user-1", "create");
  private final AuditLog audit2 =
      baseLog(Instant.parse("2024-01-01T11:00:00Z"), "user-2", "update");
  private final AuditLog audit3 =
      baseLog(Instant.parse("2024-01-01T12:00:00Z"), "user-1", "delete");

  @BeforeEach
  void setUp() {
    Page<AuditLog> page = new PageImpl<>(List.of(audit1, audit2, audit3), PageRequest.of(0, 50), 3);
    when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
  }

  private static AuditLog baseLog(Instant occurredAt, String principalId, String action) {
    AuditLog l = new AuditLog();
    l.setId(UUID.randomUUID());
    l.setOccurredAt(occurredAt);
    l.setPrincipalType(PrincipalType.API_KEY);
    l.setPrincipalId(principalId);
    l.setAction(action);
    l.setTargetType("api-key");
    l.setTargetId("target-1");
    l.setSuccess(true);
    l.setDetails("ok");
    return l;
  }

  // ─── GET /api/v1/audit-log ──────────────────────────────────────────────────

  @Test
  void list_returnsPagedResults() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit-log"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50));
  }

  @Test
  void list_filtersByPrincipalId() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit-log").param("principalId", "user-1"))
        .andExpect(status().isOk());

    // Spec is functional; verify the repository was invoked (filter is built & applied internally)
    ArgumentCaptor<Specification<AuditLog>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(repo).findAll(specCaptor.capture(), any(Pageable.class));
    // The captured spec must be non-null (i.e. controller built one)
    org.assertj.core.api.Assertions.assertThat(specCaptor.getValue()).isNotNull();
  }

  @Test
  void list_filtersByAction() throws Exception {
    mockMvc.perform(get("/api/v1/audit-log").param("action", "create")).andExpect(status().isOk());

    ArgumentCaptor<Specification<AuditLog>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(repo).findAll(specCaptor.capture(), any(Pageable.class));
    org.assertj.core.api.Assertions.assertThat(specCaptor.getValue()).isNotNull();
  }

  @Test
  void list_filtersByDateRange() throws Exception {
    OffsetDateTime from = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime to = OffsetDateTime.of(2024, 1, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    mockMvc
        .perform(get("/api/v1/audit-log").param("from", from.toString()).param("to", to.toString()))
        .andExpect(status().isOk());

    ArgumentCaptor<Specification<AuditLog>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(repo).findAll(specCaptor.capture(), any(Pageable.class));
    org.assertj.core.api.Assertions.assertThat(specCaptor.getValue()).isNotNull();
  }

  @Test
  void list_filtersBySuccess() throws Exception {
    mockMvc.perform(get("/api/v1/audit-log").param("success", "true")).andExpect(status().isOk());

    ArgumentCaptor<Specification<AuditLog>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(repo).findAll(specCaptor.capture(), any(Pageable.class));
    org.assertj.core.api.Assertions.assertThat(specCaptor.getValue()).isNotNull();
  }

  @Test
  void list_filtersByTargetType() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit-log").param("targetType", "api-key"))
        .andExpect(status().isOk());

    ArgumentCaptor<Specification<AuditLog>> specCaptor =
        ArgumentCaptor.forClass(Specification.class);
    verify(repo).findAll(specCaptor.capture(), any(Pageable.class));
    org.assertj.core.api.Assertions.assertThat(specCaptor.getValue()).isNotNull();
  }

  @Test
  void list_defaultPaging_is0_50() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit-log"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(50));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repo).findAll(any(Specification.class), pageableCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageNumber())
        .isEqualTo(0);
    org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize())
        .isEqualTo(50);
  }

  @Test
  void list_customPaging() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit-log").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(10));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repo).findAll(any(Specification.class), pageableCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageNumber())
        .isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize())
        .isEqualTo(10);
  }
}
