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
