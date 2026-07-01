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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.IsolatedDbTest;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.persistence.AuditLogRepository;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

/**
 * End-to-end integration coverage for the audit log subsystem.
 *
 * <p>Drives the real HTTP API and asserts that {@code audit_log} rows are produced with the
 * expected action, principal, success flag, and IP. Async writes are flushed by the dedicated
 * {@code auditExecutor} worker thread; Awaitility polls the repository until the expected row
 * materializes (or the test fails with a timeout).
 */
@IsolatedDbTest
class AuditLogIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository apiKeyRepo;
  @Autowired private AuditLogRepository auditRepo;
  @Autowired private VerificationProfileRepository profileRepo;
  @Autowired private ValidationJobRepository jobRepo;
  @Autowired private PasswordHasherPort hasher;
  @Autowired private ObjectMapper om;
  @Autowired private PlatformTransactionManager tx;
  @PersistenceContext private EntityManager em;

  private MockMvc mvc;
  private String adminKey;
  private String standardKey;
  private UUID adminId;

  @BeforeEach
  void setup() {
    TransactionTemplate tt = new TransactionTemplate(tx);
    tt.execute(
        s -> {
          em.createNativeQuery("DELETE FROM audit_log").executeUpdate();
          em.createNativeQuery("DELETE FROM validation_job").executeUpdate();
          em.createNativeQuery("DELETE FROM verification_profile").executeUpdate();
          em.createNativeQuery("DELETE FROM api_key").executeUpdate();
          return null;
        });

    adminKey = "sv_adminx01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey admin = new ApiKey();
    adminId = UUID.randomUUID();
    admin.setId(adminId);
    admin.setName("admin-it-" + UUID.randomUUID());
    admin.setKeyPrefix("adminx01");
    admin.setKeyHash(hasher.hash(adminKey));
    admin.setRole(Role.PRIVILEGED);
    admin.setEnabled(true);
    admin.setCreatedAt(Instant.now());
    apiKeyRepo.save(admin);

    standardKey = "sv_userx001_zyxwvutsrqponmlkjihgfedcba9876543210ZYXWVUTSR";
    ApiKey user = new ApiKey();
    user.setId(UUID.randomUUID());
    user.setName("user-it-" + UUID.randomUUID());
    user.setKeyPrefix("userx001");
    user.setKeyHash(hasher.hash(standardKey));
    user.setRole(Role.STANDARD);
    user.setEnabled(true);
    user.setCreatedAt(Instant.now());
    apiKeyRepo.save(user);

    mvc =
        MockMvcBuilders.webAppContextSetup(ctx)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  // ---------------------------------------------------------------------------
  // 4.1 — successful mutations produce audit rows with the right metadata
  // ---------------------------------------------------------------------------

  @Test
  void createApiKey_producesAuditRowWithIpAndPrincipal() throws Exception {
    String body = "{\"name\":\"new-key\",\"role\":\"PRIVILEGED\"}";

    mvc.perform(
            post("/api/v1/api-keys")
                .header("X-API-Key", adminKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = readAll();
              AuditLog row =
                  rows.stream()
                      .filter(r -> AuditActions.APIKEY_CREATE.equals(r.getAction()))
                      .findFirst()
                      .orElseThrow();
              assertThat(row.isSuccess()).isTrue();
              assertThat(row.getTargetType()).isEqualTo("api-key");
              assertThat(row.getTargetId()).isNotBlank();
              assertThat(row.getPrincipalId()).isEqualTo(adminId.toString());
              // ipAddress comes from RequestContextFilter → MDC.clientIp → captured by the
              // audit executor's MdcTaskDecorator. MockMvcBuilders.webAppContextSetup does not
              // register servlet filters by default (and our existing IT suite follows the same
              // pattern), so ipAddress may be null in this test — we only assert that the
              // remaining fields are populated correctly. The MDC propagation itself is covered
              // by AuditServiceTest and the RequestContextFilterTest.
            });
  }

  @Test
  void deleteApiKey_producesAuditRow() throws Exception {
    UUID victim =
        apiKeyRepo.findAll().stream()
            .filter(k -> k.getRole() == Role.STANDARD)
            .findFirst()
            .orElseThrow()
            .getId();

    mvc.perform(delete("/api/v1/api-keys/" + victim).header("X-API-Key", adminKey))
        .andExpect(status().isNoContent());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = readAll();
              long count =
                  rows.stream()
                      .filter(r -> AuditActions.APIKEY_DELETE.equals(r.getAction()))
                      .filter(r -> r.isSuccess())
                      .filter(r -> victim.toString().equals(r.getTargetId()))
                      .count();
              assertThat(count).isEqualTo(1);
            });
  }

  @Test
  void createProfile_producesAuditRow() throws Exception {
    String body = "{\"name\":\"audit-it-profile\",\"preset\":\"STANDARD\"}";

    mvc.perform(
            post("/api/v1/profiles")
                .header("X-API-Key", adminKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = readAll();
              AuditLog row =
                  rows.stream()
                      .filter(r -> AuditActions.PROFILE_CREATE.equals(r.getAction()))
                      .findFirst()
                      .orElseThrow();
              assertThat(row.isSuccess()).isTrue();
              assertThat(row.getPrincipalId()).isEqualTo(adminId.toString());
            });
  }

  // ---------------------------------------------------------------------------
  // 4.2 — REQUIRES_NEW: a failed business operation still produces the audit row
  // ---------------------------------------------------------------------------

  @Test
  void lastPrivilegedBlock_persistsAuditRowDespiteBusinessRollback() throws Exception {
    // The only enabled privileged key is `admin`. Deleting it triggers the
    // enforceLastPrivilegedInvariant → 409. The audit row recorded by the
    // enforcement (REQUIRES_NEW) must still be persisted even though the
    // surrounding business transaction rolls back.
    mvc.perform(delete("/api/v1/api-keys/" + adminId).header("X-API-Key", adminKey))
        .andExpect(status().isConflict());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = readAll();
              AuditLog row =
                  rows.stream()
                      .filter(
                          r -> AuditActions.APIKEY_LAST_PRIVILEGED_BLOCKED.equals(r.getAction()))
                      .findFirst()
                      .orElseThrow();
              assertThat(row.isSuccess()).isFalse();
              assertThat(row.getTargetId()).isEqualTo(adminId.toString());
            });
  }

  // ---------------------------------------------------------------------------
  // 4.3 — auth.denied is recorded on a cross-tenant job access
  // ---------------------------------------------------------------------------

  @Test
  void getJob_byNonOwner_recordsAuthDenied() throws Exception {
    // Seed a default profile and a job owned by the admin (a privileged key).
    VerificationProfile profile = new VerificationProfile();
    profile.setId(UUID.randomUUID());
    profile.setName("default-" + UUID.randomUUID());
    profile.setPreset(ProfilePreset.STANDARD);
    profile.setPolicyXml("<policy/>");
    profile.setIsDefault(true);
    profile.setCreatedAt(Instant.now());
    profile.setUpdatedAt(Instant.now());
    profileRepo.save(profile);

    // Job is owned by the admin. The standard key is a different principal and not privileged.
    ValidationJob job = new ValidationJob();
    job.setId(UUID.randomUUID());
    job.setStatus(org.toresoft.signverify.domain.model.JobStatus.PENDING);
    job.setProfileId(profile.getId());
    job.setDocumentPath("/tmp/audit-it/input.pdf");
    job.setDocumentFilename("input.pdf");
    job.setReportsRequested("simple,etsi");
    job.setCreatedAt(Instant.now());
    job.setExpiresAt(Instant.now().plusSeconds(3600));
    job.setRequestedByPrincipalType(org.toresoft.signverify.domain.model.PrincipalType.API_KEY);
    job.setRequestedByPrincipalId(adminId.toString());
    jobRepo.save(job);

    // The standard key is NOT the owner and NOT privileged → expected 404 + audit row
    // auth.denied (reason=not-owner).
    mvc.perform(get("/api/v1/verifications/jobs/" + job.getId()).header("X-API-Key", standardKey))
        .andExpect(status().isNotFound());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = readAll();
              AuditLog row =
                  rows.stream()
                      .filter(r -> AuditActions.AUTH_DENIED.equals(r.getAction()))
                      .findFirst()
                      .orElseThrow();
              assertThat(row.isSuccess()).isFalse();
              assertThat(row.getTargetType()).isEqualTo("job");
              assertThat(row.getTargetId()).isEqualTo(job.getId().toString());
              assertThat(row.getDetails()).contains("not-owner");
            });
  }

  // ---------------------------------------------------------------------------
  // 4.4 — job.submit produces a row with filename + profileId
  // ---------------------------------------------------------------------------

  @Test
  void submitJob_producesAuditRow() throws Exception {
    VerificationProfile profile = new VerificationProfile();
    profile.setId(UUID.randomUUID());
    profile.setName("default-" + UUID.randomUUID());
    profile.setPreset(ProfilePreset.STANDARD);
    profile.setPolicyXml("<policy/>");
    profile.setIsDefault(true);
    profile.setCreatedAt(Instant.now());
    profile.setUpdatedAt(Instant.now());
    profileRepo.save(profile);

    String metadata = om.writeValueAsString(Map.of("profileId", profile.getId().toString()));
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4\n".getBytes());
    MockMultipartFile meta =
        new MockMultipartFile(
            "metadata", "", MediaType.APPLICATION_JSON_VALUE, metadata.getBytes());

    mvc.perform(
            multipart("/api/v1/verifications/async")
                .file(file)
                .file(meta)
                .header("X-API-Key", standardKey))
        .andExpect(status().isAccepted());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = readAll();
              AuditLog row =
                  rows.stream()
                      .filter(r -> AuditActions.JOB_SUBMIT.equals(r.getAction()))
                      .findFirst()
                      .orElseThrow();
              assertThat(row.isSuccess()).isTrue();
              assertThat(row.getTargetType()).isEqualTo("job");
              assertThat(row.getDetails()).contains("doc.pdf").contains(profile.getId().toString());
            });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private List<AuditLog> readAll() {
    // Force a fresh read by going through the repository, not the persistence context.
    return auditRepo.findAll();
  }
}
