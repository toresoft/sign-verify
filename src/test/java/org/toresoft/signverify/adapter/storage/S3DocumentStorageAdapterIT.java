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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Round-trip against a real S3-compatible server through the full Spring wiring
 * (S3StorageConfiguration → S3DocumentStorageAdapter). Uses the maintained {@code pgsty/minio}
 * fork: upstream {@code minio/minio} was archived (Feb 2026) and is no longer CVE-patched.
 *
 * <p>{@code disabledWithoutDocker = true}: the whole class needs a live Docker daemon for the MinIO
 * container. The CI {@code test} stage runs {@code mvn verify} without a Docker service (only
 * {@code package:docker} has dind), so without this guard JUnit would error the build there instead
 * of skipping. The S3 adapter's logic is still exercised in CI by the mocked {@code
 * S3DocumentStorageAdapterTest} and {@code StorageAdapterWiringTest}; this IT adds the real
 * round-trip wherever Docker is available (developer machines, dind runners).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class S3DocumentStorageAdapterIT {

  private static final String BUCKET = "sign-verify-jobs";
  private static final String JOB_ID = "job-it-1";

  @Container
  private static final MinIOContainer MINIO =
      new MinIOContainer(
          DockerImageName.parse("pgsty/minio:RELEASE.2026-06-18T00-00-00Z")
              .asCompatibleSubstituteFor("minio/minio"));

  @DynamicPropertySource
  static void storageProperties(DynamicPropertyRegistry registry) {
    // @Container has already started MINIO by the time this runs. Provision the bucket before
    // Spring
    // constructs S3DocumentStorageAdapter, whose constructor performs a fail-fast headBucket.
    try (S3Client bootstrap =
        S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
      bootstrap.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }
    // Feed the MinIO credentials to the SDK default chain (SystemPropertyCredentialsProvider)
    // rather
    // than to app.storage.s3.* properties: production must never carry credentials in app config,
    // so
    // S3StorageConfiguration only reads the default chain. Cleared in tearDown().
    System.setProperty("aws.accessKeyId", MINIO.getUserName());
    System.setProperty("aws.secretAccessKey", MINIO.getPassword());
    registry.add("app.storage.type", () -> "s3");
    registry.add("app.storage.s3.endpoint", MINIO::getS3URL);
    registry.add("app.storage.s3.region", () -> "us-east-1");
    registry.add("app.storage.s3.bucket", () -> BUCKET);
    registry.add("app.storage.s3.path-style-access", () -> "true");
  }

  @AfterAll
  static void tearDown() {
    System.clearProperty("aws.accessKeyId");
    System.clearProperty("aws.secretAccessKey");
  }

  @Autowired private DocumentStoragePort storage;

  @Test
  void storageBeanIsS3Adapter() {
    assertThat(storage).isInstanceOf(S3DocumentStorageAdapter.class);
  }

  @Test
  void storeThenRead_returnsSameBytes() {
    byte[] content = "eIDAS document bytes".getBytes();
    String key = storage.storeInput(JOB_ID, "doc.pdf", content);
    byte[] out = storage.read(key);
    assertThat(out).containsExactly(content);
  }

  @Test
  void delete_makesKeyUnreadable() {
    String key = storage.storeResult(JOB_ID, "{}".getBytes());
    storage.delete(key);
    assertThatThrownBy(() -> storage.read(key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read fail: " + key);
  }

  @Test
  void read_missingKey_throwsIllegalState() {
    assertThatThrownBy(() -> storage.read(JOB_ID + "/nope"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("read fail:");
  }

  @Test
  void constructor_failsFastWhenBucketMissing() {
    // Exercise the constructor's headBucket guard directly against a non-existent bucket, using a
    // throwaway client bound to the same MinIO endpoint.
    try (S3Client raw =
        S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
      assertThatThrownBy(
              () -> new S3DocumentStorageAdapter(raw, "does-not-exist", new SimpleMeterRegistry()))
          .isInstanceOf(S3Exception.class);
    }
  }
}
