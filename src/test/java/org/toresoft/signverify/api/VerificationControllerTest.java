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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.VerificationService;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.ValidationResult;
import org.toresoft.signverify.security.Principal;

@WebMvcTest(controllers = VerificationController.class)
class VerificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private VerificationService service;

  @MockitoBean
  private org.toresoft.signverify.application.VerificationProfileService profileService;

  @MockitoBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;
  @MockitoBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  private final Principal owner =
      new Principal(PrincipalType.API_KEY, "owner-key-1", Role.STANDARD, "owner");

  private static Authentication authFor(Principal p) {
    return new UsernamePasswordAuthenticationToken(
        p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
  }

  private static MockMultipartFile sampleFile() {
    return new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4".getBytes());
  }

  private void stubOkResponse() {
    var result =
        new ValidationResult("PAdES", "TOTAL_PASSED", null, 1, Map.of(), List.of(), List.of());
    when(service.verifySync(any(VerificationService.VerifyRequest.class)))
        .thenReturn(new VerificationService.VerifyResponse("Default", false, result));
  }

  @Test
  void verify_withDedicatedParts_passesParsedValuesToService() throws Exception {
    stubOkResponse();
    UUID profileId = UUID.randomUUID();
    when(profileService.resolveId(null, profileId.toString())).thenReturn(profileId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(
                    new MockMultipartFile(
                        "profileId", "", "text/plain", profileId.toString().getBytes()))
                .file(
                    new MockMultipartFile(
                        "profileOverrides", "", "application/json", "{\"x\":1}".getBytes()))
                .file(new MockMultipartFile("reports", "", "text/plain", "simple,etsi".getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isOk());

    ArgumentCaptor<VerificationService.VerifyRequest> captor =
        ArgumentCaptor.forClass(VerificationService.VerifyRequest.class);
    verify(service).verifySync(captor.capture());
    VerificationService.VerifyRequest req = captor.getValue();
    assertThat(req.profileId()).isEqualTo(profileId);
    assertThat(req.overrides()).containsEntry("x", 1);
    assertThat(req.reports()).containsExactlyInAnyOrder(ReportType.SIMPLE, ReportType.ETSI);
  }

  @Test
  void verify_withLegacyMetadata_stillWorks() throws Exception {
    stubOkResponse();
    UUID profileId = UUID.randomUUID();
    String metadata = "{\"profileId\":\"" + profileId + "\",\"reports\":[\"simple\"]}";

    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(
                    new MockMultipartFile("metadata", "", "application/json", metadata.getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isOk());

    ArgumentCaptor<VerificationService.VerifyRequest> captor =
        ArgumentCaptor.forClass(VerificationService.VerifyRequest.class);
    verify(service).verifySync(captor.capture());
    assertThat(captor.getValue().reports()).containsExactly(ReportType.SIMPLE);
  }

  @Test
  void verify_withBothMetadataAndParts_returns400() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("reports", "", "text/plain", "simple".getBytes()))
                .file(
                    new MockMultipartFile(
                        "metadata", "", "application/json", "{\"profileId\":\"x\"}".getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isBadRequest());

    verify(service, never()).verifySync(any());
  }

  @Test
  void verify_byProfileName_resolvesToProfileId() throws Exception {
    stubOkResponse();
    UUID profileId = UUID.randomUUID();
    when(profileService.resolveId("AGID", null)).thenReturn(profileId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("profile", "", "text/plain", "AGID".getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isOk());

    ArgumentCaptor<VerificationService.VerifyRequest> captor =
        ArgumentCaptor.forClass(VerificationService.VerifyRequest.class);
    verify(service).verifySync(captor.capture());
    assertThat(captor.getValue().profileId()).isEqualTo(profileId);
  }

  @Test
  void verify_profileNameAndId_returns400() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("profile", "", "text/plain", "AGID".getBytes()))
                .file(
                    new MockMultipartFile(
                        "profileId", "", "text/plain", UUID.randomUUID().toString().getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isBadRequest());

    verify(service, never()).verifySync(any());
  }

  @Test
  void verify_unknownProfileName_returns404() throws Exception {
    when(profileService.resolveId("nope", null))
        .thenThrow(
            org.toresoft.signverify.domain.exception.AppException.notFound("profile not found"));

    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("profile", "", "text/plain", "nope".getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isNotFound());

    verify(service, never()).verifySync(any());
  }
}
