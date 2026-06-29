package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.toresoft.signverify.application.TslService;
import org.toresoft.signverify.domain.model.TslRefresh;

class TslReadinessIndicatorTest {

  @Test
  void up_with_details_when_ready() {
    var tsl = mock(TslService.class);
    when(tsl.isReady()).thenReturn(true);
    when(tsl.getCertificateCount()).thenReturn(123L);
    var refresh = new TslRefresh();
    refresh.setStatus(org.toresoft.signverify.domain.model.RefreshStatus.SUCCESS);
    refresh.setStartedAt(Instant.parse("2026-06-28T10:00:00Z"));
    refresh.setCompletedAt(Instant.parse("2026-06-28T10:01:00Z"));
    when(tsl.getLastRefresh()).thenReturn(Optional.of(refresh));

    var health = new TslReadinessIndicator(tsl).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("certificateCount", 123L)
        .containsEntry(
            "lastRefreshStatus", org.toresoft.signverify.domain.model.RefreshStatus.SUCCESS)
        .containsEntry("lastRefreshAt", "2026-06-28T10:01Z");
  }

  @Test
  void out_of_service_when_not_ready() {
    var tsl = mock(TslService.class);
    when(tsl.isReady()).thenReturn(false);
    when(tsl.getCertificateCount()).thenReturn(0L);
    when(tsl.getLastRefresh()).thenReturn(Optional.empty());

    var health = new TslReadinessIndicator(tsl).health();

    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    assertThat(health.getDetails()).containsEntry("lastRefreshStatus", "NONE");
  }
}
