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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

  private final AudienceValidator validator = new AudienceValidator(List.of("sign-verify"));

  @Test
  void accepts_token_whose_audience_contains_the_expected_value() {
    OAuth2TokenValidatorResult result = validator.validate(jwtWithAud(List.of("sign-verify")));
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void accepts_when_one_of_several_configured_audiences_matches() {
    var multi = new AudienceValidator(List.of("sign-verify", "sign-verify-admin"));
    OAuth2TokenValidatorResult result = multi.validate(jwtWithAud(List.of("other", "sign-verify")));
    assertThat(result.hasErrors()).isFalse();
  }

  @Test
  void rejects_token_for_a_different_audience() {
    OAuth2TokenValidatorResult result = validator.validate(jwtWithAud(List.of("legacy-spa")));
    assertThat(result.hasErrors()).isTrue();
    assertThat(result.getErrors()).anyMatch(e -> "invalid_token".equals(e.getErrorCode()));
  }

  @Test
  void rejects_token_with_no_audience_claim() {
    Jwt jwt =
        new Jwt(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "user1"));
    OAuth2TokenValidatorResult result = validator.validate(jwt);
    assertThat(result.hasErrors()).isTrue();
  }

  @Test
  void constructor_rejects_empty_accepted_audiences_to_avoid_failing_open() {
    assertThatThrownBy(() -> new AudienceValidator(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Jwt jwtWithAud(List<String> aud) {
    return new Jwt(
        "t",
        Instant.now(),
        Instant.now().plusSeconds(60),
        Map.of("alg", "none"),
        Map.of("sub", "user1", "aud", aud));
  }
}
