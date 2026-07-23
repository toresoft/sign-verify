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
package org.toresoft.signverify.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Builds the {@link S3Client} used by {@code S3DocumentStorageAdapter}. Active only when {@code
 * app.storage.type=s3}, so a deployment on the default filesystem backend never constructs an S3
 * client.
 *
 * <p>Credentials come exclusively from the SDK default chain (env vars, IAM roles/IRSA, container
 * credentials, profiles) — never from application properties, so no secret is ever carried in
 * committed config. For local MinIO-style endpoints, feed the default chain via the standard {@code
 * AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY} env vars (or {@code aws.accessKeyId}/ {@code
 * aws.secretAccessKey} system properties), not app config.
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageConfiguration {

  @Bean
  public S3Client s3Client(
      @Value("${app.storage.s3.region}") String region,
      @Value("${app.storage.s3.endpoint:}") String endpoint,
      @Value("${app.storage.s3.path-style-access:false}") boolean pathStyleAccess) {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(30))
                    .apiCallAttemptTimeout(Duration.ofSeconds(10))
                    .build());
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
    }
    return builder.build();
  }
}
