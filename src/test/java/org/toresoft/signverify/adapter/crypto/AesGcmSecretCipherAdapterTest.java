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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.port.SecretCipherPort;

class AesGcmSecretCipherAdapterTest {

  // 32 raw bytes => 44-char base64 (with padding).
  private static final String VALID_32_BYTE_KEY_B64 =
      "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

  private static final String SHORT_16_BYTE_KEY_B64 = "AAECAwQFBgcICQoLDA0ODw=="; // 16 raw bytes

  private SecretCipherPort newAdapter(String masterKey) {
    return new AesGcmSecretCipherAdapter(masterKey);
  }

  private SecretCipherPort newValidAdapter() {
    return newAdapter(VALID_32_BYTE_KEY_B64);
  }

  @Test
  void roundtrip_preservesPlaintext() {
    SecretCipherPort cipher = newValidAdapter();

    String[] inputs = {
      "hello", "a", "the quick brown fox jumps over the lazy dog", "x".repeat(1024), ""
    };

    for (String input : inputs) {
      String encrypted = cipher.encrypt(input);
      String decrypted = cipher.decrypt(encrypted);
      assertThat(decrypted).as("roundtrip for input of length %d", input.length()).isEqualTo(input);
    }
  }

  @Test
  void encrypt_producesDifferentCiphertextForSamePlaintext() {
    SecretCipherPort cipher = newValidAdapter();

    String a = cipher.encrypt("identical-plaintext");
    String b = cipher.encrypt("identical-plaintext");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void decrypt_handlesUnicode() {
    SecretCipherPort cipher = newValidAdapter();

    String unicode = "café résumé 日本語 🔐 Привет";
    String encrypted = cipher.encrypt(unicode);

    assertThat(cipher.decrypt(encrypted)).isEqualTo(unicode);
  }

  @Test
  void constructor_rejectsNullKey() {
    assertThatThrownBy(() -> newAdapter(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.security.master-key not configured");
  }

  @Test
  void constructor_rejectsBlankKey() {
    assertThatThrownBy(() -> newAdapter(""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.security.master-key not configured");

    assertThatThrownBy(() -> newAdapter("   "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.security.master-key not configured");
  }

  @Test
  void constructor_rejectsWrongLengthKey() {
    assertThatThrownBy(() -> newAdapter(SHORT_16_BYTE_KEY_B64))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("256-bit");
  }

  @Test
  void constructor_acceptsValid32ByteKey() {
    SecretCipherPort cipher = newAdapter(VALID_32_BYTE_KEY_B64);
    assertThat(cipher).isNotNull();
  }
}
