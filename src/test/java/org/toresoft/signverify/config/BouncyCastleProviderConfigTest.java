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
package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

class BouncyCastleProviderConfigTest {

  @Test
  void registersBouncyCastleProvider() {
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
    assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNull();

    new BouncyCastleProviderConfig().registerBouncyCastle();

    assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();
  }

  @Test
  void isIdempotent() {
    new BouncyCastleProviderConfig().registerBouncyCastle();
    new BouncyCastleProviderConfig().registerBouncyCastle();

    long count =
        java.util.Arrays.stream(Security.getProviders())
            .filter(p -> p.getName().equals(BouncyCastleProvider.PROVIDER_NAME))
            .count();
    assertThat(count).isEqualTo(1);
  }
}
