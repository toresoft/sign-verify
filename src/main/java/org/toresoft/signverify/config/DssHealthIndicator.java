package org.toresoft.signverify.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports DSS validation wiring as healthy once the application context has started. */
@Component("dss")
public class DssHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up().withDetail("trustSource", "configured").build();
  }
}
