package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class DssHealthIndicatorTest {

  @Test
  void always_up_when_context_started() {
    assertThat(new DssHealthIndicator().health().getStatus()).isEqualTo(Status.UP);
  }
}
