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
