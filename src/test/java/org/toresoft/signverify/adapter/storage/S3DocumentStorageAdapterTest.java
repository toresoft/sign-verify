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
package org.toresoft.signverify.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3DocumentStorageAdapterTest {

  private static final String BUCKET = "test-bucket";
  private static final String JOB_ID = "job-123";

  private S3Client s3;
  private SimpleMeterRegistry meters;
  private S3DocumentStorageAdapter adapter;

  @BeforeEach
  void setUp() {
    s3 = mock(S3Client.class);
    meters = new SimpleMeterRegistry();
    // Constructor fail-fast check must succeed for the happy-path tests.
    when(s3.headBucket(any(HeadBucketRequest.class)))
        .thenReturn(HeadBucketResponse.builder().build());
    adapter = new S3DocumentStorageAdapter(s3, BUCKET, meters);
  }

  @Test
  void storeInput_putsSanitizedKey_andReturnsIt() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

    String key = adapter.storeInput(JOB_ID, "my file!.pdf", "hi".getBytes());

    assertThat(key).isEqualTo("job-123/input-my_file_.pdf");
    ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(req.capture(), any(RequestBody.class));
    assertThat(req.getValue().bucket()).isEqualTo(BUCKET);
    assertThat(req.getValue().key()).isEqualTo("job-123/input-my_file_.pdf");
    assertThat(
            meters
                .get("signverify.storage.s3.operation")
                .tags("operation", "put_input", "outcome", "success")
                .timer()
                .count())
        .isEqualTo(1);
    assertThat(
            meters
                .get("signverify.storage.s3.bytes")
                .tag("direction", "upload")
                .summary()
                .totalAmount())
        .isEqualTo(2.0);
  }

  @Test
  void storeInput_nullFilename_defaultsToFile() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

    String key = adapter.storeInput(JOB_ID, null, new byte[] {1});

    assertThat(key).isEqualTo("job-123/input-file");
  }

  @Test
  void storeResult_putsResultJsonKey_andReturnsIt() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(null);

    String key = adapter.storeResult(JOB_ID, "{}".getBytes());

    assertThat(key).isEqualTo("job-123/result.json");
    ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(req.capture(), any(RequestBody.class));
    assertThat(req.getValue().key()).isEqualTo("job-123/result.json");
  }

  @Test
  void read_returnsObjectBytes_andRecordsDownload() {
    byte[] content = "payload".getBytes();
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

    byte[] out = adapter.read("job-123/result.json");

    assertThat(out).containsExactly(content);
    assertThat(
            meters
                .get("signverify.storage.s3.bytes")
                .tag("direction", "download")
                .summary()
                .totalAmount())
        .isEqualTo((double) content.length);
  }

  @Test
  void read_missingKey_throwsIllegalStateWithParityMessage() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("nope").build());

    assertThatThrownBy(() -> adapter.read("job-123/missing"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read fail: job-123/missing");
    assertThat(
            meters
                .get("signverify.storage.s3.operation")
                .tags("operation", "read", "outcome", "error")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void delete_removesKey() {
    when(s3.deleteObject(any(DeleteObjectRequest.class))).thenReturn(null);

    adapter.delete("job-123/result.json");

    ArgumentCaptor<DeleteObjectRequest> req = ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3).deleteObject(req.capture());
    assertThat(req.getValue().key()).isEqualTo("job-123/result.json");
  }

  @Test
  void delete_nullPath_isNoOp() {
    adapter.delete(null);
    verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void delete_s3Error_isSwallowed() {
    when(s3.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(S3Exception.builder().message("boom").build());

    // best-effort: must not throw
    adapter.delete("job-123/result.json");
    assertThat(
            meters
                .get("signverify.storage.s3.operation")
                .tags("operation", "delete", "outcome", "error")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void delete_networkError_isSwallowed() {
    when(s3.deleteObject(any(DeleteObjectRequest.class)))
        .thenThrow(SdkClientException.builder().message("connection reset").build());

    // best-effort: network blips must not abort JobCleanupScheduler
    adapter.delete("job-123/result.json");
    assertThat(
            meters
                .get("signverify.storage.s3.operation")
                .tags("operation", "delete", "outcome", "error")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  void read_networkError_throwsIllegalStateWithParityMessage() {
    when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(SdkClientException.builder().message("timeout").build());

    assertThatThrownBy(() -> adapter.read("job-123/result.json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read fail: job-123/result.json");
  }
}
