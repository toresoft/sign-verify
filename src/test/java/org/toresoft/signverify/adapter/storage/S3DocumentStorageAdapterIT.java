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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Round-trip against a real S3-compatible server. Uses the maintained {@code pgsty/minio} fork:
 * upstream {@code minio/minio} was archived (Feb 2026) and is no longer CVE-patched.
 */
@Testcontainers
class S3DocumentStorageAdapterIT {

  private static final String BUCKET = "sign-verify-jobs";
  private static final String JOB_ID = "job-it-1";

  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer(
          DockerImageName.parse("pgsty/minio:RELEASE.2026-06-18T00-00-00Z")
              .asCompatibleSubstituteFor("minio/minio"));

  private static S3Client s3;

  @BeforeAll
  static void setUp() {
    s3 =
        S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build();
    s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
  }

  private S3DocumentStorageAdapter newAdapter() {
    return new S3DocumentStorageAdapter(s3, BUCKET, new SimpleMeterRegistry());
  }

  @Test
  void storeThenRead_returnsSameBytes() {
    S3DocumentStorageAdapter adapter = newAdapter();
    byte[] content = "eIDAS document bytes".getBytes();

    String key = adapter.storeInput(JOB_ID, "doc.pdf", content);
    byte[] out = adapter.read(key);

    assertThat(out).containsExactly(content);
  }

  @Test
  void delete_makesKeyUnreadable() {
    S3DocumentStorageAdapter adapter = newAdapter();
    String key = adapter.storeResult(JOB_ID, "{}".getBytes());

    adapter.delete(key);

    assertThatThrownBy(() -> adapter.read(key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read fail: " + key);
  }

  @Test
  void read_missingKey_throwsIllegalState() {
    S3DocumentStorageAdapter adapter = newAdapter();

    assertThatThrownBy(() -> adapter.read(JOB_ID + "/nope"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read fail:");
  }

  @Test
  void constructor_failsFastWhenBucketMissing() {
    assertThatThrownBy(
            () -> new S3DocumentStorageAdapter(s3, "does-not-exist", new SimpleMeterRegistry()))
        .isInstanceOf(S3Exception.class);
  }
}
