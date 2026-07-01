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
package org.toresoft.signverify.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.AsyncJobService;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

/**
 * Unit tests for {@link AsyncVerificationController}. These tests use {@link WebMvcTest} to load
 * only the MVC slice and mock the controller's collaborators.
 */
@WebMvcTest(controllers = AsyncVerificationController.class)
@org.springframework.context.annotation.Import(
    AsyncVerificationControllerTest.PermissiveSecurity.class)
class AsyncVerificationControllerTest {

  static class PermissiveSecurity {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    org.springframework.security.web.SecurityFilterChain testFilterChain(
        org.springframework.security.config.annotation.web.builders.HttpSecurity http)
        throws Exception {
      http.csrf(c -> c.disable())
          .authorizeHttpRequests(a -> a.anyRequest().permitAll())
          .httpBasic(b -> b.disable())
          .formLogin(f -> f.disable());
      return http.build();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper om;

  @MockBean private AsyncJobService asyncService;

  @MockBean private ValidationJobRepository jobRepository;

  @MockBean private DocumentStoragePort storage;

  @MockBean private org.toresoft.signverify.application.AuditService audit;
  @MockBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;
  @MockBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  private final Principal owner =
      new Principal(PrincipalType.API_KEY, "owner-key-1", Role.STANDARD, "owner");
  private final Principal other =
      new Principal(PrincipalType.API_KEY, "other-key-1", Role.STANDARD, "other");
  private final Principal privileged =
      new Principal(PrincipalType.API_KEY, "admin-key-1", Role.PRIVILEGED, "admin");

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext().setAuthentication(authFor(owner, Role.STANDARD));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static Authentication authFor(Principal p, Role role) {
    return new UsernamePasswordAuthenticationToken(
        p, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  private static MockMultipartFile sampleFile() {
    return new MockMultipartFile(
        "file", "test.pdf", "application/pdf", "%PDF-1.4\n%fake".getBytes());
  }

  // ─── POST /async ───────────────────────────────────────────────────────────

  @Test
  void postAsync_returns202WithJobId() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void postAsync_setsLocationHeader() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted())
        .andExpect(
            header()
                .string(
                    "Location",
                    org.hamcrest.Matchers.endsWith("/api/v1/verifications/jobs/" + jobId)));
  }

  @Test
  void postAsync_parsesMetadata() throws Exception {
    UUID jobId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    String metadata =
        "{"
            + "\"profileId\":\""
            + profileId
            + "\","
            + "\"reports\":[\"simple\",\"etsi\"],"
            + "\"callbackUrl\":\"https://hook.example/submit\","
            + "\"callbackSecret\":\"shh\","
            + "\"callbackAlgorithm\":\"HmacSHA256\","
            + "\"profileOverrides\":{\"x\":1}"
            + "}";
    MockMultipartFile meta =
        new MockMultipartFile("metadata", "", "application/json", metadata.getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .file(meta)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted());

    ArgumentCaptor<AsyncJobService.SubmitRequest> captor =
        ArgumentCaptor.forClass(AsyncJobService.SubmitRequest.class);
    verify(asyncService).submit(captor.capture(), eq(owner));
    AsyncJobService.SubmitRequest req = captor.getValue();
    assertThat(req.profileId()).isEqualTo(profileId);
    assertThat(req.reports()).containsExactlyInAnyOrder(ReportType.SIMPLE, ReportType.ETSI);
    assertThat(req.callbackUrl()).isEqualTo("https://hook.example/submit");
    assertThat(req.callbackSecret()).isEqualTo("shh");
    assertThat(req.callbackAlgorithm()).isEqualTo("HmacSHA256");
    assertThat(req.overridesJson()).contains("\"x\":1");
  }

  @Test
  void postAsync_defaultsReports_whenNotProvided() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    // metadata with no reports
    MockMultipartFile meta =
        new MockMultipartFile("metadata", "", "application/json", "{}".getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .file(meta)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted());

    ArgumentCaptor<AsyncJobService.SubmitRequest> captor =
        ArgumentCaptor.forClass(AsyncJobService.SubmitRequest.class);
    verify(asyncService).submit(captor.capture(), eq(owner));
    assertThat(captor.getValue().reports())
        .containsExactlyInAnyOrder(ReportType.SIMPLE, ReportType.ETSI);
  }

  @Test
  void postAsync_handlesMissingMetadata() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted());

    verify(asyncService, times(1))
        .submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class));
  }

  // ─── GET /jobs/{jobId} ─────────────────────────────────────────────────────

  private ValidationJob baseJob(UUID id, Principal requester) {
    ValidationJob j = new ValidationJob();
    j.setId(id);
    j.setStatus(JobStatus.PENDING);
    j.setReportsRequested("simple,etsi");
    j.setDocumentPath("storage/" + id);
    j.setDocumentFilename("test.pdf");
    j.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
    j.setExpiresAt(Instant.parse("2024-01-02T00:00:00Z"));
    j.setRequestedByPrincipalType(requester.type());
    j.setRequestedByPrincipalId(requester.id());
    return j;
  }

  @Test
  void getJob_returnsJobStatus() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner);
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$.jobId").value(jobId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.createdAt").exists())
        .andExpect(jsonPath("$.expiresAt").exists());
  }

  @Test
  void getJob_returnsResult_forCompletedJob() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner);
    job.setStatus(JobStatus.COMPLETED);
    job.setCompletedAt(Instant.parse("2024-01-01T00:10:00Z"));
    job.setResultPath("results/" + jobId + "/report.json");
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(storage.read("results/" + jobId + "/report.json"))
        .thenReturn("{\"indication\":\"VALID\"}".getBytes());

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.result").exists());
  }

  @Test
  void getJob_returnsError_forFailedJob() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner);
    job.setStatus(JobStatus.FAILED);
    job.setErrorMessage("invalid signature");
    job.setCompletedAt(Instant.parse("2024-01-01T00:10:00Z"));
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.error").value("invalid signature"));
  }

  @Test
  void getJob_returns410_forDeletedJob() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner);
    job.setStatus(JobStatus.DELETED);
    job.setOriginalStatus(JobStatus.COMPLETED);
    job.setDeletedAt(Instant.parse("2024-01-01T01:00:00Z"));
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isGone())
        .andExpect(jsonPath("$.originalStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.deletedAt").exists());
  }

  @Test
  void getJob_returns404_whenNotFound() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isNotFound());
  }

  @Test
  void getJob_returns404_forNonOwner() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner); // owned by `owner`
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(other, Role.STANDARD))))
        .andExpect(status().isNotFound());

    // must not leak status: verify no body status, and we don't update lastAccessedAt
    verify(jobRepository, never()).save(any(ValidationJob.class));
  }

  @Test
  void getJob_allowsPrivilegedUser_toViewAnyJob() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner); // owned by `owner`
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(privileged, Role.PRIVILEGED))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(jobId.toString()));
  }

  @Test
  void getJob_updatesLastAccessedAt() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner);
    job.setLastAccessedAt(Instant.parse("2024-01-01T00:00:00Z"));
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    Instant before = Instant.now();
    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isOk());

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(jobRepository).save(captor.capture());
    ValidationJob saved = captor.getValue();
    assertThat(saved.getLastAccessedAt()).isNotNull();
    assertThat(saved.getLastAccessedAt()).isAfterOrEqualTo(before);
  }
}
