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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-compatible implementation of {@link DocumentStoragePort}. Keys mirror the filesystem layout:
 *
 * <pre>
 *   {jobId}/input-{safeName}
 *   {jobId}/result.json
 * </pre>
 *
 * <p>The returned "path" is the object key (relative, no bucket/scheme); it is persisted in the DB
 * and passed back to {@link #read}/{@link #delete}. Active only when {@code app.storage.type=s3}.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3DocumentStorageAdapter implements DocumentStoragePort {

  private static final Logger log = LoggerFactory.getLogger(S3DocumentStorageAdapter.class);

  private static final String INPUT_PREFIX = "input-";
  private static final String RESULT_NAME = "result.json";
  private static final String DEFAULT_INPUT_NAME = "file";
  private static final String SAFE_NAME_REGEX = "[^a-zA-Z0-9._-]";

  private static final String METRIC_OP = "signverify.storage.s3.operation";
  private static final String METRIC_BYTES = "signverify.storage.s3.bytes";

  private final S3Client s3;
  private final String bucket;
  private final MeterRegistry meters;

  public S3DocumentStorageAdapter(
      S3Client s3, @Value("${app.storage.s3.bucket}") String bucket, MeterRegistry meters) {
    this.s3 = s3;
    this.bucket = bucket;
    this.meters = meters;
    // Fail-fast: refuse to start if the bucket is missing/unreachable, mirroring the filesystem
    // adapter's Files.createDirectories in its constructor. Propagates S3Exception on failure.
    s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
  }

  @Override
  public String storeInput(String jobId, String filename, byte[] content) {
    String name = (filename == null) ? DEFAULT_INPUT_NAME : safeName(filename);
    String key = jobId + "/" + INPUT_PREFIX + name;
    put("put_input", key, content);
    return key;
  }

  @Override
  public String storeResult(String jobId, byte[] content) {
    String key = jobId + "/" + RESULT_NAME;
    put("put_result", key, content);
    return key;
  }

  @Override
  public byte[] read(String path) {
    validateKey(path);
    Timer.Sample sample = Timer.start(meters);
    try {
      ResponseBytes<GetObjectResponse> resp =
          s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(path).build());
      byte[] bytes = resp.asByteArray();
      stop(sample, "read", "success");
      meters.summary(METRIC_BYTES, "direction", "download").record(bytes.length);
      return bytes;
    } catch (S3Exception e) {
      stop(sample, "read", "error");
      throw new IllegalStateException("read fail: " + path, e);
    }
  }

  @Override
  public void delete(String path) {
    if (path == null || path.isBlank()) {
      return;
    }
    Timer.Sample sample = Timer.start(meters);
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(path).build());
      stop(sample, "delete", "success");
    } catch (S3Exception e) {
      stop(sample, "delete", "error");
      // best-effort delete; nothing actionable for the caller
      log.warn("s3 delete failed for key {}: {}", path, e.getMessage());
    }
  }

  private void put(String operation, String key, byte[] content) {
    Timer.Sample sample = Timer.start(meters);
    try {
      s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).build(),
          RequestBody.fromBytes(content));
      stop(sample, operation, "success");
      meters.summary(METRIC_BYTES, "direction", "upload").record(content.length);
    } catch (S3Exception e) {
      stop(sample, operation, "error");
      throw new IllegalStateException(operation + " fail", e);
    }
  }

  private void stop(Timer.Sample sample, String operation, String outcome) {
    sample.stop(meters.timer(METRIC_OP, "operation", operation, "outcome", outcome));
  }

  private static void validateKey(String key) {
    if (key == null || key.isBlank() || key.startsWith("/")) {
      throw new IllegalArgumentException("invalid storage key: " + key);
    }
  }

  private static String safeName(String filename) {
    return filename.replaceAll(SAFE_NAME_REGEX, "_");
  }
}
