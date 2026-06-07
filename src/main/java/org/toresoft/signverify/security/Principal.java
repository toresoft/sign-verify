package org.toresoft.signverify.security;

import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;

public record Principal(PrincipalType type, String id, Role role, String displayName) {

  public static Principal system() {
    return new Principal(PrincipalType.SYSTEM, "system", Role.PRIVILEGED, "system");
  }
}
