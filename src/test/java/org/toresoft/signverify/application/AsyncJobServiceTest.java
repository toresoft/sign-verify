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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SecretCipherPort;
import org.toresoft.signverify.domain.port.SecretGeneratorPort;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@ExtendWith(MockitoExtension.class)
class AsyncJobServiceTest {

  @Mock private ValidationJobRepository repo;
  @Mock private DocumentStoragePort storage;
  @Mock private SecretCipherPort cipher;
  @Mock private AuditService audit;
  @Mock private SecretGeneratorPort secretGenerator;

  private AsyncJobService service;
  private final Principal actor =
      new Principal(PrincipalType.API_KEY, "k1", Role.STANDARD, "tester");

  @BeforeEach
  void setUp() {
    service =
        new AsyncJobService(
            repo,
            storage,
            cipher,
            secretGenerator,
            audit,
            5,
            10,
            Duration.ofHours(1),
            "HmacSHA256",
            List.of("HmacSHA256", "HmacSHA512"));
    lenient().when(repo.countActiveGlobal()).thenReturn(0L);
    lenient()
        .when(repo.countActiveByPrincipal(any(PrincipalType.class), anyString()))
        .thenReturn(0L);
    lenient().when(secretGenerator.generateCallbackSecret()).thenReturn("svcbk_test-secret-value");
  }

  private AsyncJobService.SubmitRequest baseRequest() {
    return new AsyncJobService.SubmitRequest(
        new byte[] {1, 2, 3},
        "doc.pdf",
        UUID.randomUUID(),
        "{\"k\":\"v\"}",
        Set.of(ReportType.SIMPLE, ReportType.ETSI),
        "https://callback.example/hook",
        "HmacSHA256");
  }

  @Test
  void submit_createsJobWithExpectedFields() {
    var req = baseRequest();
    var profileId = req.profileId();
    when(storage.storeInput(anyString(), anyString(), any()))
        .thenReturn("/var/lib/sign-verify/jobs/abc/input-doc.pdf");

    AsyncJobService.SubmitResult returned = service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    ValidationJob saved = captor.getValue();

    assertThat(saved.getId()).isEqualTo(returned.jobId());
    assertThat(saved.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(saved.getProfileId()).isEqualTo(profileId);
    assertThat(saved.getProfileOverrides()).isEqualTo("{\"k\":\"v\"}");
    assertThat(saved.getReportsRequested()).isEqualTo("simple,etsi");
    assertThat(saved.getDocumentPath()).isEqualTo("/var/lib/sign-verify/jobs/abc/input-doc.pdf");
    assertThat(saved.getDocumentFilename()).isEqualTo("doc.pdf");
    assertThat(saved.getCallbackUrl()).isEqualTo("https://callback.example/hook");
    assertThat(saved.getCallbackAlgorithm()).isEqualTo("HmacSHA256");
    assertThat(saved.getRequestedByPrincipalType()).isEqualTo(PrincipalType.API_KEY);
    assertThat(saved.getRequestedByPrincipalId()).isEqualTo("k1");
  }

  @Test
  void submit_rejectsCallbackAlgorithmOutsideAllowlist() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            null,
            null,
            Set.of(ReportType.SIMPLE),
            "https://callback.example/hook",
            "HmacMD5");

    AppException ex =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            AppException.class, () -> service.submit(req, actor));
    assertThat(ex.getStatus()).isEqualTo(400);
    assertThat(ex.getDetail()).contains("callbackAlgorithm");
    verify(repo, never()).save(any());
  }

  @Test
  void submit_acceptsAllowedAlgorithm_caseInsensitively() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            null,
            null,
            Set.of(ReportType.SIMPLE),
            "https://callback.example/hook",
            "hmacsha512");
    when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

    service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackAlgorithm()).isEqualTo("hmacsha512");
  }

  @Test
  void submit_malformedCallbackUrl_isRejected() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            "ftp://not-http/hook",
            "HmacSHA256");

    assertThatThrownBy(() -> service.submit(req, actor))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getDetail()).contains("callbackUrl"));
    verify(repo, never()).save(any());
  }

  @Test
  void submit_noCallbackUrl_nullSecret_doesNotEncrypt() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            null,
            "HmacSHA256");

    service.submit(req, actor);

    verify(cipher, never()).encrypt(anyString());
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackSecretCipher()).isNull();
  }

  @Test
  void submit_nullCallbackAlgorithm_defaultsToHmacSHA256() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            "https://callback.example/hook",
            null);

    service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackAlgorithm()).isEqualTo("HmacSHA256");
  }

  @Test
  void submit_providedCallbackAlgorithm_isPreserved() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            "https://callback.example/hook",
            "HmacSHA512");

    service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackAlgorithm()).isEqualTo("HmacSHA512");
  }

  @Test
  void submit_emptyReports_defaultsToSimpleEtsi() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1}, "doc.pdf", UUID.randomUUID(), null, Set.of(), null, null);

    service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getReportsRequested()).isEqualTo("simple,etsi");
  }

  @Test
  void submit_providedReports_joinedAsCsv() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE, ReportType.DIAGNOSTIC),
            null,
            null);

    service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getReportsRequested()).isEqualTo("simple,diagnostic");
  }

  @Test
  void submit_throwsBackpressure_whenGlobalLimitReached() {
    when(repo.countActiveGlobal()).thenReturn(10L);

    assertThatThrownBy(() -> service.submit(baseRequest(), actor))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("global");
  }

  @Test
  void submit_throwsBackpressure_whenPrincipalLimitReached() {
    when(repo.countActiveByPrincipal(eq(PrincipalType.API_KEY), eq("k1"))).thenReturn(5L);

    assertThatThrownBy(() -> service.submit(baseRequest(), actor))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("per-principal");
  }

  @Test
  void submit_storesInputFile_andPersistsReturnedPath() {
    var req = baseRequest();
    when(storage.storeInput(anyString(), anyString(), any())).thenReturn("storage/path/doc");

    service.submit(req, actor);

    verify(storage, times(1)).storeInput(anyString(), eq("doc.pdf"), eq(new byte[] {1, 2, 3}));
    // The persisted documentPath must be the actual path returned by storage, not a synthetic
    // "storage/<jobId>" directory (regression guard: the worker reads exactly this path).
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getDocumentPath()).isEqualTo("storage/path/doc");
  }

  @Test
  void submit_setsExpiryToNowPlusTtl() {
    Instant before = Instant.now();

    service.submit(baseRequest(), actor);

    Instant after = Instant.now();
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    ValidationJob saved = captor.getValue();

    Duration ttl = Duration.ofHours(1);
    Instant minExpected = before.plus(ttl).minusSeconds(1);
    Instant maxExpected = after.plus(ttl).plusSeconds(1);
    assertThat(saved.getExpiresAt()).isBetween(minExpected, maxExpected);
    assertThat(saved.getCreatedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    assertThat(Duration.between(saved.getCreatedAt(), saved.getExpiresAt()))
        .isCloseTo(ttl, Duration.ofSeconds(2));
  }

  @Test
  void submit_returnsJobId() {
    AsyncJobService.SubmitResult r = service.submit(baseRequest(), actor);

    assertThat(r.jobId()).isNotNull();
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(r.jobId());
  }

  @Test
  void submit_auditsSuccessfulSubmission() {
    var req = baseRequest();

    AsyncJobService.SubmitResult r = service.submit(req, actor);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Map<String, Object>> details =
        ArgumentCaptor.forClass(java.util.Map.class);
    verify(audit, times(1))
        .log(
            eq(actor),
            eq(AuditActions.JOB_SUBMIT),
            eq("job"),
            eq(r.jobId().toString()),
            eq(true),
            details.capture());
    assertThat(details.getValue())
        .containsEntry("filename", "doc.pdf")
        .containsEntry("profileId", req.profileId().toString());
  }

  @Test
  void submit_generatesAndEncryptsSecretWhenCallbackUrlPresent() {
    var req = baseRequest();
    when(secretGenerator.generateCallbackSecret()).thenReturn("svcbk_gen");
    when(cipher.encrypt("svcbk_gen")).thenReturn("enc-cipher");
    when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

    service.submit(req, actor);

    verify(secretGenerator, times(1)).generateCallbackSecret();
    verify(cipher, times(1)).encrypt("svcbk_gen");
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackSecretCipher()).isEqualTo("enc-cipher");
  }

  @Test
  void submit_returnsGeneratedSecretInResult() {
    var req = baseRequest();
    when(secretGenerator.generateCallbackSecret()).thenReturn("svcbk_gen");
    when(cipher.encrypt("svcbk_gen")).thenReturn("enc");
    when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

    AsyncJobService.SubmitResult result = service.submit(req, actor);

    assertThat(result.jobId()).isNotNull();
    // The PLAINTEXT is returned, not the ciphertext.
    assertThat(result.callbackSecret()).isEqualTo("svcbk_gen");
  }

  @Test
  void submit_omitsSecretWhenNoCallbackUrl() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            null,
            "HmacSHA256");
    when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

    AsyncJobService.SubmitResult result = service.submit(req, actor);

    verify(secretGenerator, never()).generateCallbackSecret();
    verify(cipher, never()).encrypt(anyString());
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackSecretCipher()).isNull();
    assertThat(result.callbackSecret()).isNull();
  }
}
