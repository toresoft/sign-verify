package org.toresoft.signverify.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.application.TslService;

@Component("tslReadiness")
public class TslReadinessIndicator implements HealthIndicator {

  private final TslService tslService;

  public TslReadinessIndicator(TslService s) {
    this.tslService = s;
  }

  @Override
  public Health health() {
    return tslService.isReady() ? Health.up().build() : Health.outOfService().build();
  }
}
