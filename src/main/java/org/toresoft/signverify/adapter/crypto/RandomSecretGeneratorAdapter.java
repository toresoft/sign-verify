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

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.SecretGeneratorPort;

/**
 * Generates callback secrets using a cryptographically strong {@link SecureRandom}. Each secret is
 * 256 bits of entropy, base64url-encoded (no padding), and prefixed with {@code svcbk_} so it is
 * distinguishable from other tokens in logs and redaction rules.
 */
@Component
public class RandomSecretGeneratorAdapter implements SecretGeneratorPort {

  private static final int ENTROPY_BYTES = 32; // 256-bit
  private static final String PREFIX = "svcbk_";

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generateCallbackSecret() {
    byte[] buf = new byte[ENTROPY_BYTES];
    random.nextBytes(buf);
    return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }
}
