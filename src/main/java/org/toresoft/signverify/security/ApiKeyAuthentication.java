package org.toresoft.signverify.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {

  private final Principal principal;

  public ApiKeyAuthentication(Principal principal) {
    super(buildAuthorities(principal));
    this.principal = principal;
    super.setAuthenticated(true);
  }

  private static Collection<? extends GrantedAuthority> buildAuthorities(Principal p) {
    return List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name()));
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  @Override
  public String getName() {
    return principal.id();
  }
}
