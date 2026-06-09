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
