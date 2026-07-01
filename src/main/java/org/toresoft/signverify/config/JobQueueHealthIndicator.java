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

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.persistence.ValidationJobRepository;

/**
 * Informational indicator: exposes the async validation queue depth (PENDING + RUNNING jobs).
 * Always reports UP — a backlog is not an unhealthy state — so it never flips the aggregate health
 * used by k8s probes.
 */
@Component("jobQueue")
public class JobQueueHealthIndicator implements HealthIndicator {

  private final ValidationJobRepository jobRepository;

  public JobQueueHealthIndicator(ValidationJobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @Override
  public Health health() {
    return Health.up().withDetail("activeJobs", jobRepository.countActiveGlobal()).build();
  }
}
