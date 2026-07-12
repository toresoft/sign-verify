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
package org.toresoft.signverify.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RandomSecretGeneratorAdapterTest {

  private final RandomSecretGeneratorAdapter gen = new RandomSecretGeneratorAdapter();

  @Test
  void generate_startsWithSvcbkPrefix() {
    assertThat(gen.generateCallbackSecret()).startsWith("svcbk_");
  }

  @Test
  void generate_decodesTo256Bits() {
    String s = gen.generateCallbackSecret();
    // Strip "svcbk_" then base64url-decode (no padding) -> must be exactly 32 bytes.
    String payload = s.substring("svcbk_".length());
    byte[] decoded = Base64.getUrlDecoder().decode(payload + paddingFor(payload));
    assertThat(decoded).hasSize(32);
  }

  @Test
  void generate_isBase64UrlWithoutPadding() {
    String s = gen.generateCallbackSecret();
    String payload = s.substring("svcbk_".length());
    assertThat(payload).doesNotContain("=");
    // base64url alphabet only (A-Z a-z 0-9 - _)
    assertThat(payload).matches("^[A-Za-z0-9_-]+$");
  }

  @RepeatedTest(50)
  void generate_producesUniqueValues() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      assertThat(seen.add(gen.generateCallbackSecret()))
          .as("duplicate secret after %d generations", i)
          .isTrue();
    }
  }

  // Base64url without padding must be re-padded before the standard decoder accepts it.
  private static String paddingFor(String payload) {
    int mod = payload.length() % 4;
    return mod == 0 ? "" : "=".repeat(4 - mod);
  }
}
