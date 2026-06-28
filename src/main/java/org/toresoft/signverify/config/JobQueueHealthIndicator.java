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
