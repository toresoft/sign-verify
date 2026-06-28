package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TsdSmokeTest {

  @Autowired private TestRestTemplate rest;

  @Value("${local.server.port}")
  private int port;

  private String apiKey;

  @BeforeEach
  void setUp() throws IOException {
    apiKey = Files.readString(Path.of("/tmp/sign-verify-test/bootstrap-api-key.txt")).strip();
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
