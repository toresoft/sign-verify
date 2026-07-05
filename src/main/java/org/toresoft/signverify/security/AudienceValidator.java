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

import java.util.Collections;
import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects JWTs whose {@code aud} claim does not name this resource server. The default issuer-based
 * decoder validates only signature, issuer and expiry — not audience — so a token minted by the
 * same IdP for a different client in the shared realm would otherwise be accepted here
 * (cross-audience token confusion, potentially escalating to {@code PRIVILEGED} via the {@code
 * roles} claim). This validator is composed with the issuer default on the {@code JwtDecoder} to
 * close that gap.
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final List<String> acceptedAudiences;

  /**
   * @param acceptedAudiences at least one audience this server accepts; empty is rejected so the
   *     check can never silently fail open.
   */
  public AudienceValidator(List<String> acceptedAudiences) {
    if (acceptedAudiences == null || acceptedAudiences.isEmpty()) {
      throw new IllegalArgumentException("at least one accepted audience must be configured");
    }
    this.acceptedAudiences = List.copyOf(acceptedAudiences);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    List<String> aud = jwt.getAudience();
    if (aud != null && !Collections.disjoint(aud, acceptedAudiences)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            "invalid_token",
            "the required audience " + acceptedAudiences + " is missing from the token",
            null));
  }
}
