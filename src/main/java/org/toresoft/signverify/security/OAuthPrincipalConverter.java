package org.toresoft.signverify.security;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;

public class OAuthPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final String roleClaim;
  private final List<String> privilegedValues;

  public OAuthPrincipalConverter(String roleClaim, List<String> privilegedValues) {
    this.roleClaim = roleClaim;
    this.privilegedValues = privilegedValues;
  }

  public Principal toPrincipal(Jwt jwt) {
    Role role = isPrivileged(jwt) ? Role.PRIVILEGED : Role.STANDARD;
    String displayName = jwt.getClaimAsString("preferred_username");
    if (displayName == null) displayName = jwt.getSubject();
    return new Principal(PrincipalType.OAUTH_USER, jwt.getSubject(), role, displayName);
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Principal p = toPrincipal(jwt);
    return new JwtAuthAdapter(jwt, p);
  }

  private boolean isPrivileged(Jwt jwt) {
    Object claim = jwt.getClaim(roleClaim);
    if (claim instanceof Collection<?> c) {
      for (Object v : c) {
        if (privilegedValues.contains(String.valueOf(v))) return true;
      }
    } else if (claim instanceof String s) {
      for (String v : s.split("[ ,]")) {
        if (privilegedValues.contains(v)) return true;
      }
    }
    return false;
  }

  static class JwtAuthAdapter extends AbstractAuthenticationToken {
    private final Jwt jwt;
    private final Principal principal;

    JwtAuthAdapter(Jwt jwt, Principal principal) {
      super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
      this.jwt = jwt;
      this.principal = principal;
      setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
      return jwt.getTokenValue();
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
}
