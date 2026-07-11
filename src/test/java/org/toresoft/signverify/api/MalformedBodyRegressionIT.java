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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.ApiKeyService;

/**
 * Regression pin for {@link GlobalExceptionHandler#handleNotReadable} (lines 59-64). Malformed JSON
 * sent to an endpoint that expects a JSON body must produce HTTP 400 with {@code code:
 * validation.invalid-input} in {@code application/problem+json}. This path is fragile during
 * Jackson upgrades (e.g. Spring Boot 3 → 4) because a changed exception hierarchy can silently slip
 * through to the generic handler and become 500 {@code internal-error}.
 */
@WebMvcTest(controllers = ApiKeyController.class)
@org.springframework.context.annotation.Import(MalformedBodyRegressionIT.PermissiveSecurity.class)
class MalformedBodyRegressionIT {

  static class PermissiveSecurity {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    org.springframework.security.web.SecurityFilterChain testFilterChain() {
      return new org.springframework.security.web.SecurityFilterChain() {
        @Override
        public boolean matches(jakarta.servlet.http.HttpServletRequest request) {
          return true;
        }

        @Override
        public java.util.List<jakarta.servlet.Filter> getFilters() {
          return java.util.List.of();
        }
      };
    }
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApiKeyService apiKeyService;

  @MockitoBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;

  @MockitoBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  @Test
  void malformedJsonBody_returns400_withValidationInvalidInputCode() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.parseMediaType("application/problem+json")))
        .andExpect(jsonPath("$.code").value("validation.invalid-input"));
  }
}
