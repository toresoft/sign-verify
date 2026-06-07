package org.toresoft.signverify.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.IsolatedDbTest;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@IsolatedDbTest
class ExtractionControllerIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository keys;
  @Autowired private PasswordHasherPort hasher;

  private MockMvc mvc;
  private String apiKey;

  @BeforeEach
  void setup() {
    apiKey = "sv_ext01ab_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("extract-" + UUID.randomUUID());
    k.setKeyPrefix("ext01ab_");
    k.setKeyHash(hasher.hash(apiKey));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    keys.save(k);
    mvc =
        MockMvcBuilders.webAppContextSetup(ctx)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  @Test
  void extract_pades_returns_200_with_headers() throws Exception {
    byte[] pdf =
        Files.readAllBytes(Path.of("src/test/resources/signatures/sample-pades-valid.pdf"));
    var filePart = new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf);

    mvc.perform(multipart("/api/v1/extractions").file(filePart).header("X-API-Key", apiKey))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Signature-Format"))
        .andExpect(header().exists("X-Document-Count"));
  }
}
