package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TsdSmokeTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private ApiKeyRepository keys;
  @Autowired private PasswordHasherPort hasher;

  @Value("${local.server.port}")
  private int port;

  private String apiKey;

  @BeforeEach
  void setUp() {
    apiKey = "sv_tsdsmk01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("tsd-smoke-" + UUID.randomUUID());
    k.setKeyPrefix("tsdsmk01");
    k.setKeyHash(hasher.hash(apiKey));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    keys.save(k);
    System.out.println("[TsdSmokeTest] port=" + port);
  }

  @Test
  void tsd_verification_endpoint_returns_200() throws Exception {
    byte[] tsdBytes =
        new ClassPathResource("assets/tsd/sample-rfc5544.tsd").getInputStream().readAllBytes();

    var headers = new HttpHeaders();
    headers.set("X-API-Key", apiKey);
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add(
        "file",
        new org.springframework.mock.web.MockMultipartFile(
                "file", "sample-rfc5544.tsd", "application/octet-stream", tsdBytes)
            .getResource());
    body.add("metadata", "{\"reports\":[\"simple\"]}");

    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

    ResponseEntity<String> response =
        rest.postForEntity(
            "http://localhost:" + port + "/api/v1/verifications", request, String.class);

    assertThat(response.getStatusCode())
        .as("TSD verification should return 200, not a parse/auth error")
        .isEqualTo(HttpStatus.OK);

    String responseBody = response.getBody();
    assertThat(responseBody).isNotNull();
    assertThat(responseBody).contains("\"indication\"");
    assertThat(responseBody).contains("\"signatureFormat\"");
  }
}
