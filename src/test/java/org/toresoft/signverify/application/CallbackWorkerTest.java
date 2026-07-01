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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort.DispatchResult;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.SecretCipherPort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

/**
 * Unit tests for {@link CallbackWorker}.
 *
 * <p>All tests are pure Mockito unit tests; no Spring context is started. These tests are expected
 * to FAIL until the {@code CallbackWorker} class is implemented (FASE 9, Task 9.7).
 */
@ExtendWith(MockitoExtension.class)
class CallbackWorkerTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final String CALLBACK_URL = "https://callback.example/hook";
  private static final String ALGO = "HMAC-SHA256";
  private static final String CIPHER = "enc:v1:abcdef";
  private static final String PLAIN = "super-secret";

  @Mock private ValidationJobRepository repo;
  @Mock private CallbackDispatcherPort dispatcher;
  @Mock private SecretCipherPort cipher;
  @Mock private DocumentStoragePort storage;

  private final ObjectMapper om = new ObjectMapper();

  private CallbackWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new CallbackWorker(
            repo,
            dispatcher,
            cipher,
            storage,
            om,
            MAX_ATTEMPTS,
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30)),
            List.of(200, 202),
            List.of(400, 410));
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private ValidationJob newCompletedJob() {
    return newCompletedJob(0, null, null);
  }

  private ValidationJob newCompletedJob(int attempts, String errorMessage, String resultPath) {
    ValidationJob job = new ValidationJob();
    job.setId(UUID.randomUUID());
    job.setStatus(attempts > 0 || errorMessage != null ? JobStatus.FAILED : JobStatus.COMPLETED);
    job.setCallbackUrl(CALLBACK_URL);
    job.setCallbackAlgorithm(ALGO);
    job.setCallbackSecretCipher(CIPHER);
    job.setCallbackAttempts(attempts);
    job.setNextCallbackAt(Instant.now().minusSeconds(1));
    job.setResultPath(resultPath);
    job.setErrorMessage(errorMessage);
    return job;
  }

  private void stubSecret() {
    lenient().when(cipher.decrypt(CIPHER)).thenReturn(PLAIN);
  }

  private void stubResultJson(String json) {
    lenient().when(storage.read(anyString())).thenReturn(json.getBytes());
  }

  private ValidationJob captureLastSave() {
    ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
    verify(repo, times(1)).save(captor.capture());
    return captor.getValue();
  }

  private byte[] captureBody() throws Exception {
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(dispatcher, times(1))
        .dispatch(
            anyString(),
            anyString(),
            anyString(),
            captor.capture(),
            anyString(),
            anyString(),
            anyInt());
    return captor.getValue();
  }

  // ---------------------------------------------------------------------
  // dispatch(...) tests
  // ---------------------------------------------------------------------

  @Test
  void dispatch_successfulCallback_setsDelivered() {
    ValidationJob job = newCompletedJob();
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{\"indication\":\"TOTAL_PASSED\"}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(200, null));

    worker.dispatch(job.getId());

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.DELIVERED);
    assertThat(saved.getDeliveredAt()).isNotNull();
    assertThat(saved.getLastCallbackError()).isNull();
    assertThat(saved.getCallbackAttempts()).isEqualTo(1);
  }

  @Test
  void dispatch_nonRetryableStatus_setsDeliveryFailed() {
    ValidationJob job = newCompletedJob();
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(400, null));

    worker.dispatch(job.getId());

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.DELIVERY_FAILED);
    assertThat(saved.getLastCallbackError()).contains("400");
    assertThat(saved.getNextCallbackAt()).isNull();
    assertThat(saved.getCallbackAttempts()).isEqualTo(1);
  }

  @Test
  void dispatch_maxAttemptsReached_setsDeliveryFailed() {
    ValidationJob job = newCompletedJob();
    job.setCallbackAttempts(MAX_ATTEMPTS - 1); // next attempt = MAX_ATTEMPTS
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(500, "boom"));

    worker.dispatch(job.getId());

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isEqualTo(JobStatus.DELIVERY_FAILED);
    assertThat(saved.getLastCallbackError()).isNotBlank();
    assertThat(saved.getNextCallbackAt()).isNull();
    assertThat(saved.getCallbackAttempts()).isEqualTo(MAX_ATTEMPTS);
  }

  @Test
  void dispatch_retryable_schedulesNextAttempt() {
    ValidationJob job = newCompletedJob();
    job.setCallbackAttempts(0);
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(500, "boom"));

    Instant before = Instant.now();
    worker.dispatch(job.getId());
    Instant after = Instant.now();

    ValidationJob saved = captureLastSave();
    assertThat(saved.getStatus()).isNotEqualTo(JobStatus.DELIVERED);
    assertThat(saved.getStatus()).isNotEqualTo(JobStatus.DELIVERY_FAILED);
    assertThat(saved.getNextCallbackAt()).isNotNull();
    // backoff[attempt-1] = backoff[0] = 1s
    Instant minExpected = before.plus(Duration.ofSeconds(1));
    Instant maxExpected = after.plus(Duration.ofSeconds(1));
    assertThat(saved.getNextCallbackAt()).isBetween(minExpected, maxExpected);
  }

  @Test
  void dispatch_decryptsCallbackSecret() {
    ValidationJob job = newCompletedJob();
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(200, null));

    worker.dispatch(job.getId());

    verify(cipher, times(1)).decrypt(CIPHER);
    verify(dispatcher, times(1))
        .dispatch(
            eq(CALLBACK_URL),
            eq(ALGO),
            eq(PLAIN),
            any(),
            eq(job.getId().toString()),
            anyString(),
            anyInt());
  }

  @Test
  void dispatch_sendsBodyWithResult() throws Exception {
    ValidationJob job = newCompletedJob();
    job.setResultPath("storage/result/abc");
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{\"indication\":\"TOTAL_PASSED\",\"signatureCount\":2}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(200, null));

    worker.dispatch(job.getId());

    byte[] body = captureBody();
    JsonNode tree = om.readTree(body);
    assertThat(tree.get("jobId").asText()).isEqualTo(job.getId().toString());
    assertThat(tree.has("status")).isTrue();
    assertThat(tree.has("result")).isTrue();
    assertThat(tree.get("result").get("indication").asText()).isEqualTo("TOTAL_PASSED");
    assertThat(tree.get("result").get("signatureCount").asInt()).isEqualTo(2);
  }

  @Test
  void dispatch_sendsBodyWithError_onFailedJob() throws Exception {
    ValidationJob job = newCompletedJob(0, "validation failed: bad signature", null);
    job.setStatus(JobStatus.FAILED);
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(200, null));

    worker.dispatch(job.getId());

    byte[] body = captureBody();
    JsonNode tree = om.readTree(body);
    assertThat(tree.get("jobId").asText()).isEqualTo(job.getId().toString());
    assertThat(tree.get("status").asText()).isEqualTo("FAILED");
    assertThat(tree.has("error")).isTrue();
    assertThat(tree.get("error").asText()).contains("bad signature");
  }

  @Test
  void dispatch_nullCallbackUrl_isNoOp() {
    ValidationJob job = newCompletedJob();
    job.setCallbackUrl(null);
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));

    worker.dispatch(job.getId());

    verify(dispatcher, never())
        .dispatch(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt());
    verify(repo, never()).save(any());
  }

  @Test
  void dispatch_futureNextCallbackAt_isNoOp() {
    ValidationJob job = newCompletedJob();
    job.setNextCallbackAt(Instant.now().plus(Duration.ofMinutes(5)));
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));

    worker.dispatch(job.getId());

    verify(dispatcher, never())
        .dispatch(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt());
    verify(repo, never()).save(any());
  }

  @Test
  void dispatch_incrementsCallbackAttempts() {
    ValidationJob job = newCompletedJob();
    job.setCallbackAttempts(2);
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(200, null));

    worker.dispatch(job.getId());

    ValidationJob saved = captureLastSave();
    assertThat(saved.getCallbackAttempts()).isEqualTo(3);
  }

  @Test
  void dispatch_backoffUsesCorrectIndex() {
    ValidationJob job = newCompletedJob();
    job.setCallbackAttempts(1); // next attempt = 2 → backoff[min(1, 2)] = 5s
    when(repo.findById(job.getId())).thenReturn(java.util.Optional.of(job));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(500, "boom"));

    Instant before = Instant.now();
    worker.dispatch(job.getId());
    Instant after = Instant.now();

    ValidationJob saved = captureLastSave();
    assertThat(saved.getNextCallbackAt()).isNotNull();
    Instant minExpected = before.plus(Duration.ofSeconds(5));
    Instant maxExpected = after.plus(Duration.ofSeconds(5));
    assertThat(saved.getNextCallbackAt()).isBetween(minExpected, maxExpected);
  }

  // ---------------------------------------------------------------------
  // poll() tests
  // ---------------------------------------------------------------------

  @Test
  void poll_processesDueJobs() {
    ValidationJob j1 = newCompletedJob();
    ValidationJob j2 = newCompletedJob();
    when(repo.findCallbacksDue(any(), eq(MAX_ATTEMPTS), any())).thenReturn(List.of(j1, j2));
    when(repo.findById(j1.getId())).thenReturn(java.util.Optional.of(j1));
    when(repo.findById(j2.getId())).thenReturn(java.util.Optional.of(j2));
    stubSecret();
    stubResultJson("{}");
    when(dispatcher.dispatch(
            anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt()))
        .thenReturn(new DispatchResult(200, null));

    worker.poll();

    verify(repo, times(1)).findCallbacksDue(any(), eq(MAX_ATTEMPTS), any());
    verify(repo).findById(j1.getId());
    verify(repo).findById(j2.getId());
    verify(dispatcher, times(2))
        .dispatch(anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyInt());
  }
}
