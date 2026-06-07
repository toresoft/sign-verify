package org.toresoft.signverify.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@SpringBootTest
@ActiveProfiles("test")
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
    mvc = MockMvcBuilders.webAppContextSetup(ctx)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
        .build();
  }

  @Test
  void create_then_list() throws Exception {
    String body = """
        {"name":"new-priv","role":"PRIVILEGED"}
        """;
    mvc.perform(post("/api/v1/api-keys")
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
