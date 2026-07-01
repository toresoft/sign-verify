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
    keys.deleteAll();
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
        Files.readAllBytes(Path.of("src/test/resources/assets/pades/sample-pades-valid.pdf"));
    var filePart = new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf);

    mvc.perform(multipart("/api/v1/extractions").file(filePart).header("X-API-Key", apiKey))
        .andExpect(status().isOk())
        .andExpect(header().exists("X-Signature-Format"))
        .andExpect(header().exists("X-Document-Count"));
  }

  @Test
  void extract_without_multipart_filename_still_returns_200() throws Exception {
    byte[] pdf =
        Files.readAllBytes(Path.of("src/test/resources/assets/pades/sample-pades-valid.pdf"));
    // null original filename -> app must deduce it.
    var filePart = new MockMultipartFile("file", null, "application/pdf", pdf);

    mvc.perform(multipart("/api/v1/extractions").file(filePart).header("X-API-Key", apiKey))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Signature-Format", "PAdES"));
  }

  @Test
  void extract_tsd_returns_200_and_reports_tsd_format() throws Exception {
    byte[] tsd = Files.readAllBytes(Path.of("src/test/resources/assets/tsd/sample-rfc5544.tsd"));
    var filePart = new MockMultipartFile("file", "sample.tsd", "application/octet-stream", tsd);

    mvc.perform(multipart("/api/v1/extractions").file(filePart).header("X-API-Key", apiKey))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Signature-Format", "RFC5544_TSD"))
        .andExpect(header().string("X-Document-Count", "1"));
  }
}
