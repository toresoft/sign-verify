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
