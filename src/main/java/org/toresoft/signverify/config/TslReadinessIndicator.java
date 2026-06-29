package org.toresoft.signverify.config;

import java.time.ZoneOffset;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.application.TslService;

/**
 * Reports TSL readiness and, as details, the trusted-certificate count and the last refresh outcome
 * (status + timestamp). Drives the aggregate health status (OUT_OF_SERVICE until the TSL is ready),
 * so it gates k8s readiness.
 */
@Component("tslReadiness")
public class TslReadinessIndicator implements HealthIndicator {

  private final TslService tslService;

  public TslReadinessIndicator(TslService s) {
    this.tslService = s;
  }

  @Override
  public Health health() {
    Health.Builder b = tslService.isReady() ? Health.up() : Health.outOfService();
    b.withDetail("certificateCount", tslService.getCertificateCount());
    tslService
        .getLastRefresh()
        .ifPresentOrElse(
            r -> {
              b.withDetail("lastRefreshStatus", r.getStatus());
              var at = r.getCompletedAt() != null ? r.getCompletedAt() : r.getStartedAt();
              if (at != null) b.withDetail("lastRefreshAt", at.atOffset(ZoneOffset.UTC).toString());
              b.withDetail("lastRefreshCertificatesAdded", r.getCertificatesAdded());
              b.withDetail("lastRefreshCertificatesRemoved", r.getCertificatesRemoved());
            },
            () -> b.withDetail("lastRefreshStatus", "NONE"));
    return b.build();
  }
}
