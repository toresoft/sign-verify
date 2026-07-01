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

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Configuration
public class MetricsConfiguration {

  @Bean
  public Object asyncMetrics(MeterRegistry registry, ValidationJobRepository repo) {
    registry.gauge(
        "signverify.async.jobs.pending",
        repo,
        r -> r.findAll().stream().filter(j -> j.getStatus().name().equals("PENDING")).count());
    registry.gauge(
        "signverify.async.jobs.running",
        repo,
        r -> r.findAll().stream().filter(j -> j.getStatus().name().equals("RUNNING")).count());
    return new Object();
  }
}
