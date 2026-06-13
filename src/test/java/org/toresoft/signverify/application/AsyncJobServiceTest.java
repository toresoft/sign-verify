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
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@ExtendWith(MockitoExtension.class)
class AsyncJobServiceTest {

  @Mock private ValidationJobRepository repo;
  @Mock private DocumentStoragePort storage;
  @Mock private SecretCipherPort cipher;

  private AsyncJobService service;
  private final Principal actor =
      new Principal(PrincipalType.API_KEY, "k1", Role.STANDARD, "tester");

  @BeforeEach
  void setUp() {
    service = new AsyncJobService(repo, storage, cipher, 5, 10, Duration.ofHours(1));
    lenient().when(repo.countActiveGlobal()).thenReturn(0L);
    lenient()
        .when(repo.countActiveByPrincipal(any(PrincipalType.class), anyString()))
        .thenReturn(0L);
  }

  private AsyncJobService.SubmitRequest baseRequest() {
    return new AsyncJobService.SubmitRequest(
        new byte[] {1, 2, 3},
        "doc.pdf",
        UUID.randomUUID(),
        "{\"k\":\"v\"}",
        Set.of(ReportType.SIMPLE, ReportType.ETSI),
        "https://callback.example/hook",
        "secret-123",
        "HmacSHA256");
  }

  @Test
  void submit_createsJobWithExpectedFields() {
    var req = baseRequest();
    var profileId = req.profileId();
    when(storage.storeInput(anyString(), anyString(), any()))
        .thenReturn("/var/lib/sign-verify/jobs/abc/input-doc.pdf");

    UUID returned = service.submit(req, actor);

    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    ValidationJob saved = captor.getValue();

    assertThat(saved.getId()).isEqualTo(returned);
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
  void submit_encryptsCallbackSecret() {
    var req = baseRequest();
    when(cipher.encrypt("secret-123")).thenReturn("enc-cipher");

    service.submit(req, actor);

    verify(cipher, times(1)).encrypt("secret-123");
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getCallbackSecretCipher()).isEqualTo("enc-cipher");
  }

  @Test
  void submit_callbackUrlWithoutSecret_isRejected() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            "https://callback.example/hook",
            null,
            "HmacSHA256");

    assertThatThrownBy(() -> service.submit(req, actor))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getDetail()).contains("callbackSecret"));
    verify(cipher, never()).encrypt(anyString());
    verify(repo, never()).save(any());
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
            "secret-123",
            "HmacSHA256");

    assertThatThrownBy(() -> service.submit(req, actor))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getDetail()).contains("callbackUrl"));
    verify(repo, never()).save(any());
  }

  @Test
  void submit_callbackUrlWithBlankSecret_isRejected() {
    var req =
        new AsyncJobService.SubmitRequest(
            new byte[] {1},
            "doc.pdf",
            UUID.randomUUID(),
            null,
            Set.of(ReportType.SIMPLE),
            "https://callback.example/hook",
            "   ",
            "HmacSHA256");

    assertThatThrownBy(() -> service.submit(req, actor)).isInstanceOf(AppException.class);
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
            "secret",
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
            "secret",
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
            new byte[] {1}, "doc.pdf", UUID.randomUUID(), null, Set.of(), null, null, null);

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
    UUID id = service.submit(baseRequest(), actor);

    assertThat(id).isNotNull();
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo(id);
  }
}
