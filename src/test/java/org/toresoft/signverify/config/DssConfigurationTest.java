package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import org.junit.jupiter.api.Test;

/**
 * Guards the production {@link CertificateVerifier} wiring. The DSS integration tests run with an
 * offline override ({@link OfflineDssTestConfig}) for speed, which would otherwise hide a
 * regression that silently drops online revocation in production. This plain unit test pins the
 * real bean.
 */
class DssConfigurationTest {

  @Test
  void production_verifier_wires_online_revocation_and_aia() {
    CertificateVerifier cv =
        new DssConfiguration().certificateVerifier(new TrustedListsCertificateSource());

    assertThat(cv.getOcspSource()).isInstanceOf(OnlineOCSPSource.class);
    assertThat(cv.getCrlSource()).isInstanceOf(OnlineCRLSource.class);
    assertThat(cv.getAIASource()).isInstanceOf(DefaultAIASource.class);
    assertThat(cv.isRevocationFallback()).isTrue();
  }
}
