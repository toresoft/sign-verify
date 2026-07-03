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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.toresoft.signverify.domain.model.JobStatus;
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
  void pendingGauge_delegatesToCountByStatus() {
    when(repo.countByStatus(JobStatus.PENDING)).thenReturn(3L);

    config.asyncMetrics(registry, repo);

    double value = registry.get("signverify.async.jobs.pending").gauge().value();
    assertThat(value).isEqualTo(3.0);
    // Sampled on every scrape: must be an O(1) COUNT, never a findAll() over the table.
    verify(repo).countByStatus(JobStatus.PENDING);
  }

  @Test
  void runningGauge_delegatesToCountByStatus() {
    when(repo.countByStatus(JobStatus.RUNNING)).thenReturn(2L);

    config.asyncMetrics(registry, repo);

    double value = registry.get("signverify.async.jobs.running").gauge().value();
    assertThat(value).isEqualTo(2.0);
    verify(repo).countByStatus(JobStatus.RUNNING);
  }
}
