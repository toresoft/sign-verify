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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.toresoft.signverify.domain.model.Role;

class OAuthPrincipalConverterTest {

  private final OAuthPrincipalConverter conv =
      new OAuthPrincipalConverter("roles", List.of("admin"));

  @Test
  void privileged_role_when_claim_contains_admin() {
    Jwt jwt = jwt(Map.of("sub", "user1", "roles", List.of("user", "admin")));
    var auth = conv.convert(jwt).getAuthorities();
    assertThat(auth).extracting(GrantedAuthority::getAuthority).contains("ROLE_PRIVILEGED");
  }

  @Test
  void standard_role_when_claim_no_admin() {
    Jwt jwt = jwt(Map.of("sub", "user1", "roles", List.of("user")));
    var auth = conv.convert(jwt).getAuthorities();
    assertThat(auth).extracting(GrantedAuthority::getAuthority).contains("ROLE_STANDARD");
  }

  @Test
  void principal_from_jwt() {
    Jwt jwt = jwt(Map.of("sub", "u42", "roles", List.of("admin"), "preferred_username", "Alice"));
    Principal p = conv.toPrincipal(jwt);
    assertThat(p.id()).isEqualTo("u42");
    assertThat(p.role()).isEqualTo(Role.PRIVILEGED);
    assertThat(p.displayName()).isEqualTo("Alice");
  }

  private Jwt jwt(Map<String, Object> claims) {
    Map<String, Object> headers = Map.of("alg", "none");
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), headers, claims);
  }
}
