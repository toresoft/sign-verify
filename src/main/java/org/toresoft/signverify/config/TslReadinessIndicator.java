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
