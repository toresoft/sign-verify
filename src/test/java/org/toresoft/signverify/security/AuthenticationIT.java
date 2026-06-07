package org.toresoft.signverify.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.IsolatedDbTest;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@IsolatedDbTest
class AuthenticationIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository repo;
  @Autowired private PasswordHasherPort hasher;

  private MockMvc mvc;

  private MockMvc mvc() {
    if (mvc == null)
      mvc =
          MockMvcBuilders.webAppContextSetup(ctx)
              .apply(
                  org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                      .springSecurity())
              .build();
    return mvc;
  }

  @Test
  void no_credentials_returns_401() throws Exception {
    mvc().perform(get("/internal/whoami")).andExpect(status().isUnauthorized());
  }

  @Test
  void valid_api_key_returns_200_with_principal() throws Exception {
    String plaintext = "sv_test1234_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("test-it-" + UUID.randomUUID());
    k.setKeyPrefix("test1234");
    k.setKeyHash(hasher.hash(plaintext));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    repo.save(k);

    mvc()
        .perform(get("/internal/whoami").header("X-API-Key", plaintext))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("API_KEY"))
        .andExpect(jsonPath("$.role").value("STANDARD"));
  }

  @Test
  void standard_key_forbidden_on_admin() throws Exception {
    String plaintext = "sv_stnd5678_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("stnd-" + UUID.randomUUID());
    k.setKeyPrefix("stnd5678");
    k.setKeyHash(hasher.hash(plaintext));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    repo.save(k);

    mvc()
        .perform(get("/internal/admin").header("X-API-Key", plaintext))
        .andExpect(status().isForbidden());
  }
}
