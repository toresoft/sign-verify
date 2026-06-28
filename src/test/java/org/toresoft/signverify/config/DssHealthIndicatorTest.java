package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class DssHealthIndicatorTest {

  @Test
  void up_when_certificate_verifier_present() {
    var cv = mock(eu.europa.esig.dss.spi.validation.CertificateVerifier.class);
    var indicator = new DssHealthIndicator(cv);
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }
}
