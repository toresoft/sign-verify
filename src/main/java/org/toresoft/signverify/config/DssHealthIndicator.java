package org.toresoft.signverify.config;

import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports DSS validation wiring as healthy once the CertificateVerifier bean is available. */
@Component("dss")
public class DssHealthIndicator implements HealthIndicator {

  private final CertificateVerifier certificateVerifier;

  public DssHealthIndicator(CertificateVerifier certificateVerifier) {
    this.certificateVerifier = certificateVerifier;
  }

  @Override
  public Health health() {
    return certificateVerifier != null
        ? Health.up().withDetail("trustSource", "configured").build()
        : Health.down().build();
  }
}
