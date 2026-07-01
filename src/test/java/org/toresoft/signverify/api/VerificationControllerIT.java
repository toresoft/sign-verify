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
class VerificationControllerIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository keys;
  @Autowired private PasswordHasherPort hasher;

  private MockMvc mvc;
  private String apiKey;

  @BeforeEach
  void setup() {
    apiKey = "sv_verify01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("verify-" + UUID.randomUUID());
    k.setKeyPrefix("verify01");
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
  void verify_pades_returns_indication() throws Exception {
    byte[] pdf =
        Files.readAllBytes(Path.of("src/test/resources/assets/pades/sample-pades-valid.pdf"));
    var filePart = new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf);
    var meta =
        new MockMultipartFile(
            "metadata", "metadata", "application/json", "{\"reports\":[\"simple\"]}".getBytes());

    mvc.perform(
            multipart("/api/v1/verifications")
                .file(filePart)
                .file(meta)
                .header("X-API-Key", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indication").exists())
        .andExpect(jsonPath("$.signatureFormat").exists())
        .andExpect(jsonPath("$.signatures").isArray())
        .andExpect(jsonPath("$.signatures[0].signatureLevel").exists())
        .andExpect(jsonPath("$.timestamps").isArray())
        .andExpect(jsonPath("$.reports.simple").exists());
  }
}
