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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden test that captures the exact JSON form of persisted async-job data (profileOverrides,
 * result payload, metadata) as written by Jackson 2 (Spring Boot 3.5.14). After the Jackson 2 →
 * Jackson 3 flip, re-running this test proves that historical rows and in-flight jobs can still be
 * deserialized by the new ObjectMapper.
 */
@SpringBootTest
@ActiveProfiles("test")
class PersistedJsonRereadGoldenTest {

  @Autowired private JsonMapper om;

  @Test
  void fixture_profileOverrides_canBeRead_andRoundTrips() throws Exception {
    Map<String, Object> fixture = loadFixture();

    // profileOverrides is stored as a raw JSON string in the profile_overrides TEXT column.
    // AsyncVerificationController writes it with om.writeValueAsString(...);
    // ValidationWorker reads it with om.readValue(job.getProfileOverrides(), new
    // TypeReference<>(){}).
    String profileOverridesJson = om.writeValueAsString(fixture.get("profileOverrides"));
    Map<String, Object> readBack =
        om.readValue(profileOverridesJson, new TypeReference<Map<String, Object>>() {});

    assertThat(readBack)
        .containsEntry("checkRevocation", false)
        .containsEntry("checkSignatureIntegrity", true)
        .containsEntry("checkCertificateChain", true)
        .containsEntry("checkTimestamp", false)
        .containsEntry("checkQualified", true);

    // Round-trip must preserve the exact data shape.
    String roundTripJson = om.writeValueAsString(readBack);
    Map<String, Object> roundTrip =
        om.readValue(roundTripJson, new TypeReference<Map<String, Object>>() {});
    assertThat(roundTrip).isEqualTo(readBack);
  }

  @Test
  void fixture_resultPayload_canBeRead_andRoundTrips() throws Exception {
    Map<String, Object> fixture = loadFixture();

    // ValidationWorker builds a LinkedHashMap, serializes it with om.writeValueAsBytes(resultJson),
    // and stores the bytes via DocumentStoragePort. AsyncVerificationController reads it back with
    // om.readTree(storage.read(job.getResultPath())).
    String resultJson = om.writeValueAsString(fixture.get("result"));
    JsonNode tree = om.readTree(resultJson);

    assertThat(tree.get("indication").asText()).isEqualTo("TOTAL_PASSED");
    assertThat(tree.get("subIndication").isNull()).isTrue();
    assertThat(tree.get("signatureFormat").asText()).isEqualTo("PAdES-BASELINE-B");
    assertThat(tree.get("signatureCount").asInt()).isEqualTo(1);
    assertThat(tree.get("profileUsed").asText()).isEqualTo("STANDARD");
    assertThat(tree.get("reports").get("simple").get("ok").asBoolean()).isTrue();
    assertThat(tree.get("reports").get("etsi").get("etsi").asBoolean()).isTrue();

    // Round-trip must preserve the exact data shape.
    String roundTripJson = om.writeValueAsString(tree);
    JsonNode roundTrip = om.readTree(roundTripJson);
    assertThat(roundTrip).isEqualTo(tree);
  }

  @Test
  void fixture_metadata_canBeRead_andRoundTrips() throws Exception {
    Map<String, Object> fixture = loadFixture();

    // AsyncVerificationController reads metadata from the multipart request with
    // om.readValue(metadataJson, new TypeReference<Map<String, Object>>() {}).
    String metadataJson = om.writeValueAsString(fixture.get("metadata"));
    Map<String, Object> readBack =
        om.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});

    assertThat(readBack.get("profileId")).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
    assertThat(readBack.get("callbackUrl")).isEqualTo("https://example.com/callback");
    assertThat(readBack.get("callbackAlgorithm")).isEqualTo("HMAC-SHA256");
    assertThat(readBack.get("reports")).isEqualTo(List.of("simple", "etsi"));

    // Round-trip must preserve the exact data shape.
    String roundTripJson = om.writeValueAsString(readBack);
    Map<String, Object> roundTrip =
        om.readValue(roundTripJson, new TypeReference<Map<String, Object>>() {});
    assertThat(roundTrip).isEqualTo(readBack);
  }

  @Test
  void fixture_jobId_isStableUuid() throws Exception {
    Map<String, Object> fixture = loadFixture();
    assertThat(fixture.get("jobId")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  void fixture_timestamps_areFixed() throws Exception {
    Map<String, Object> fixture = loadFixture();
    assertThat(fixture.get("createdAt")).isEqualTo("2026-01-15T10:30:00Z");
    assertThat(fixture.get("completedAt")).isEqualTo("2026-01-15T10:31:00Z");
  }

  private Map<String, Object> loadFixture() throws Exception {
    try (InputStream is = getClass().getResourceAsStream("/fixtures/persisted-job-3.5.json")) {
      return om.readValue(is, new TypeReference<Map<String, Object>>() {});
    }
  }
}
