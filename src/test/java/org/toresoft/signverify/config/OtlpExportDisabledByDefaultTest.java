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

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * The OTLP registry is on the classpath, but pushing metrics must be an explicit opt-in
 * (APP_METRICS_OTLP_ENABLED): a default boot must never open outbound connections to a collector
 * that does not exist.
 */
@SpringBootTest
@ActiveProfiles("test")
class OtlpExportDisabledByDefaultTest {

  @Autowired private ApplicationContext context;

  @Test
  void otlpRegistryAbsentByDefault() {
    assertThat(context.getBeanNamesForType(OtlpMeterRegistry.class)).isEmpty();
  }

  @Test
  void prometheusRegistryStillPresent() {
    assertThat(context.getBeanNamesForType(PrometheusMeterRegistry.class)).hasSize(1);
  }
}
