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

import jakarta.annotation.PostConstruct;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Registers BouncyCastle as a JCA security provider during context initialization (before the TSL
 * refresh runs on {@code ApplicationReadyEvent}).
 *
 * <p>Some legacy EU Trusted List certificates (e.g. early A-Trust roots) use an RSA public-key
 * encoding that the default JDK provider rejects with "invalid info structure in RSA public key",
 * so DSS cannot load them. BouncyCastle parses them correctly.
 */
@Configuration
public class BouncyCastleProviderConfig {

  private static final Logger log = LoggerFactory.getLogger(BouncyCastleProviderConfig.class);

  @PostConstruct
  void registerBouncyCastle() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
      log.info("Registered BouncyCastle JCA provider");
    } else {
      log.debug("BouncyCastle JCA provider already registered");
    }
  }
}
