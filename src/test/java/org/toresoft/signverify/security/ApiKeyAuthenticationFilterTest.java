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
package org.toresoft.signverify.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

class ApiKeyAuthenticationFilterTest {

  private final ApiKeyRepository repo = mock(ApiKeyRepository.class);
  private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
  private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(repo, hasher);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void valid_key_authenticates() throws Exception {
    String plaintext = "sv_alphabe1_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    ApiKey key = enabledKey("alphabe1", "hash");
    when(repo.findByKeyPrefix("alphabe1")).thenReturn(Optional.of(key));
    when(hasher.matches(plaintext, "hash")).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", plaintext);
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.isAuthenticated()).isTrue();
    assertThat(((Principal) auth.getPrincipal()).type()).isEqualTo(PrincipalType.API_KEY);
    verify(chain).doFilter(req, res);
  }

  @Test
  void invalid_key_returns_401() throws Exception {
    when(repo.findByKeyPrefix(any())).thenReturn(Optional.empty());

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", "sv_unknown_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verifyNoInteractions(chain);
  }

  @Test
  void disabled_key_returns_401() throws Exception {
    ApiKey key = enabledKey("alphabe1", "hash");
    key.setEnabled(false);
    when(repo.findByKeyPrefix("alphabe1")).thenReturn(Optional.of(key));

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", "sv_alphabe1_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req, res, mock(FilterChain.class));
    assertThat(res.getStatus()).isEqualTo(401);
  }

  @Test
  void no_header_passes_through() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(req, res, chain);
    verify(chain).doFilter(req, res);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private ApiKey enabledKey(String prefix, String hash) {
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("test");
    k.setKeyPrefix(prefix);
    k.setKeyHash(hash);
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    return k;
  }
}
