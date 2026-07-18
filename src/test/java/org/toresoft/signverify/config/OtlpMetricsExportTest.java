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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the app with OTLP export enabled and pointed at a WireMock collector, then verifies that
 * metrics are actually pushed over OTLP/HTTP. This is the safety net for the OTLP classpath: Boot's
 * autoconfiguration backs off silently if micrometer-registry-otlp or spring-boot-opentelemetry go
 * missing, and only this test would catch that.
 */
@SpringBootTest
@ActiveProfiles("test")
class OtlpMetricsExportTest {

  private static final WireMockServer COLLECTOR =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @DynamicPropertySource
  static void otlpProperties(DynamicPropertyRegistry registry) {
    COLLECTOR.start();
    COLLECTOR.stubFor(post(urlEqualTo("/v1/metrics")).willReturn(aResponse().withStatus(200)));
    registry.add("management.otlp.metrics.export.enabled", () -> "true");
    registry.add("management.otlp.metrics.export.url", () -> COLLECTOR.baseUrl() + "/v1/metrics");
    // Short step so the first publish happens within the awaitility window.
    registry.add("management.otlp.metrics.export.step", () -> "1s");
  }

  @AfterAll
  static void stopCollector() {
    COLLECTOR.stop();
  }

  @Autowired private ApplicationContext context;

  @Test
  void otlpRegistryPresentWhenEnabled() {
    assertThat(context.getBeanNamesForType(OtlpMeterRegistry.class)).hasSize(1);
  }

  @Test
  void pushesProtobufMetricsToCollector() {
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () ->
                COLLECTOR.verify(
                    moreThanOrExactly(1),
                    postRequestedFor(urlEqualTo("/v1/metrics"))
                        .withHeader("Content-Type", containing("application/x-protobuf"))));
  }

  @Test
  void payloadContainsApplicationMetrics() {
    // Meter names are embedded as plain UTF-8 strings inside the OTLP protobuf payload,
    // so a byte-level contains() proves our custom gauges are in the push without
    // pulling in a protobuf parser just for this assertion.
    await()
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              boolean found =
                  COLLECTOR.getAllServeEvents().stream()
                      .map(e -> new String(e.getRequest().getBody(), StandardCharsets.ISO_8859_1))
                      .anyMatch(body -> body.contains("signverify.async.jobs.pending"));
              assertThat(found).isTrue();
            });
  }
}
