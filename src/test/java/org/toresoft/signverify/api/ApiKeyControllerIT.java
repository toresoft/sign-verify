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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.IsolatedDbTest;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@IsolatedDbTest
class ApiKeyControllerIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository repo;
  @Autowired private PasswordHasherPort hasher;
  @Autowired private ObjectMapper om;

  private MockMvc mvc;
  private String adminKey;

  @BeforeEach
  void setup() {
    repo.deleteAll();
    adminKey = "sv_adminx01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("admin-it-" + UUID.randomUUID());
    k.setKeyPrefix("adminx01");
    k.setKeyHash(hasher.hash(adminKey));
    k.setRole(Role.PRIVILEGED);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    repo.save(k);
    mvc =
        MockMvcBuilders.webAppContextSetup(ctx)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  @Test
  void create_then_list() throws Exception {
    String body =
        """
        {"name":"new-priv","role":"PRIVILEGED"}
        """;
    mvc.perform(
            post("/api/v1/api-keys")
                .header("X-API-Key", adminKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.plaintextKey").exists());

    mvc.perform(get("/api/v1/api-keys").header("X-API-Key", adminKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void delete_last_privileged_returns_409() throws Exception {
    UUID adminId = repo.findAll().get(0).getId();
    mvc.perform(delete("/api/v1/api-keys/" + adminId).header("X-API-Key", adminKey))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:signverify:error:resource.conflict"));
  }
}
