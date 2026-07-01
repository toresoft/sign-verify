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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@ExtendWith(MockitoExtension.class)
class MetricsConfigurationTest {

  @Mock private ValidationJobRepository repo;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MetricsConfiguration config = new MetricsConfiguration();

  @Test
  void asyncMetrics_registersPendingGauge() {
    config.asyncMetrics(registry, repo);

    assertThat(registry.find("signverify.async.jobs.pending").gauge()).isNotNull();
  }

  @Test
  void asyncMetrics_registersRunningGauge() {
    config.asyncMetrics(registry, repo);

    assertThat(registry.find("signverify.async.jobs.running").gauge()).isNotNull();
  }

  @Test
  void pendingGauge_returnsCountOfPendingJobs() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                job(JobStatus.PENDING),
                job(JobStatus.PENDING),
                job(JobStatus.PENDING),
                job(JobStatus.RUNNING),
                job(JobStatus.RUNNING),
                job(JobStatus.COMPLETED)));

    config.asyncMetrics(registry, repo);

    double value = registry.get("signverify.async.jobs.pending").gauge().value();
    assertThat(value).isEqualTo(3.0);
  }

  @Test
  void runningGauge_returnsCountOfRunningJobs() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                job(JobStatus.PENDING),
                job(JobStatus.PENDING),
                job(JobStatus.PENDING),
                job(JobStatus.RUNNING),
                job(JobStatus.RUNNING),
                job(JobStatus.COMPLETED)));

    config.asyncMetrics(registry, repo);

    double value = registry.get("signverify.async.jobs.running").gauge().value();
    assertThat(value).isEqualTo(2.0);
  }

  @Test
  void pendingGauge_ignoresOtherStatuses() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                job(JobStatus.COMPLETED),
                job(JobStatus.FAILED),
                job(JobStatus.DELIVERED),
                job(JobStatus.DELIVERY_FAILED),
                job(JobStatus.DELETED)));

    config.asyncMetrics(registry, repo);

    double value = registry.get("signverify.async.jobs.pending").gauge().value();
    assertThat(value).isEqualTo(0.0);
  }

  @Test
  void runningGauge_ignoresOtherStatuses() {
    when(repo.findAll())
        .thenReturn(
            List.of(
                job(JobStatus.COMPLETED),
                job(JobStatus.FAILED),
                job(JobStatus.DELIVERED),
                job(JobStatus.DELIVERY_FAILED),
                job(JobStatus.DELETED)));

    config.asyncMetrics(registry, repo);

    double value = registry.get("signverify.async.jobs.running").gauge().value();
    assertThat(value).isEqualTo(0.0);
  }

  private ValidationJob job(JobStatus s) {
    ValidationJob j = new ValidationJob();
    j.setStatus(s);
    return j;
  }
}
