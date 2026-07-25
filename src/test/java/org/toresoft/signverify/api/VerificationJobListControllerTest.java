package org.toresoft.signverify.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.JobPage;
import org.toresoft.signverify.application.JobQuery;
import org.toresoft.signverify.application.JobQueryService;
import org.toresoft.signverify.application.JobSummaryDto;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.security.Principal;

@WebMvcTest(controllers = VerificationJobListController.class)
class VerificationJobListControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private JobQueryService service;
  // Collaborators pulled in by the security/filter slice; mock to satisfy the context.
  @MockitoBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;
  @MockitoBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  private final Principal standard =
      new Principal(PrincipalType.API_KEY, "me", Role.STANDARD, "me");
  private final Principal privileged =
      new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  private static Authentication authFor(Principal p) {
    return new UsernamePasswordAuthenticationToken(
        p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
  }

  // MockMvc runs the request on the test thread and the @WebMvcTest slice has no
  // SecurityContextHolder-loading filter, so the controller reads the actor from the holder.
  private void actAs(Principal p) {
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(authFor(p));
  }

  @org.junit.jupiter.api.AfterEach
  void clearContext() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  void returnsEnvelope() throws Exception {
    JobSummaryDto summary =
        new JobSummaryDto(
            java.util.UUID.fromString("491ff5a4-5745-40bd-8bc3-119582f6e89e"),
            "COMPLETED",
            new JobSummaryDto.Owner("OAUTH_USER", "user-123"),
            java.time.OffsetDateTime.parse("2026-07-14T22:13:24.272673Z"),
            null,
            null,
            java.time.OffsetDateTime.parse("2026-07-21T22:13:24.272673Z"),
            0,
            "RESULT_STORE",
            null);
    when(service.list(any(Principal.class), any(JobQuery.class)))
        .thenReturn(new JobPage(0, 50, 1, 1, List.of(summary)));
    actAs(standard);

    mvc.perform(get("/api/v1/verifications/jobs").with(authentication(authFor(standard))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value("491ff5a4-5745-40bd-8bc3-119582f6e89e"))
        .andExpect(jsonPath("$.content[0].requestedBy.type").value("OAUTH_USER"))
        .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
  }

  @Test
  void bindsRepeatedStatusAndPassesPrincipal() throws Exception {
    when(service.list(any(Principal.class), any(JobQuery.class)))
        .thenReturn(new JobPage(0, 50, 0, 0, List.of()));
    actAs(privileged);

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("status", "COMPLETED", "FAILED")
                .param("page", "2")
                .param("size", "10")
                .with(authentication(authFor(privileged))))
        .andExpect(status().isOk());

    ArgumentCaptor<JobQuery> qc = ArgumentCaptor.forClass(JobQuery.class);
    ArgumentCaptor<Principal> pc = ArgumentCaptor.forClass(Principal.class);
    verify(service).list(pc.capture(), qc.capture());
    org.assertj.core.api.Assertions.assertThat(qc.getValue().statuses())
        .containsExactly(JobStatus.COMPLETED, JobStatus.FAILED);
    org.assertj.core.api.Assertions.assertThat(qc.getValue().page()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(pc.getValue().id()).isEqualTo("admin");
  }

  @Test
  void invalidStatusValue_returns400() throws Exception {
    actAs(standard);

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("status", "NOT_A_STATUS")
                .with(authentication(authFor(standard))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void standardWithOwnerFilter_propagates400FromService() throws Exception {
    when(service.list(any(Principal.class), any(JobQuery.class)))
        .thenThrow(AppException.badRequest("owner filter requires PRIVILEGED"));
    actAs(standard);

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("ownerId", "someone")
                .with(authentication(authFor(standard))))
        .andExpect(status().isBadRequest());
  }
}
