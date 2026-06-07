package org.toresoft.signverify.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.config.TslProperties;
import org.toresoft.signverify.domain.model.RefreshTrigger;

@Component
public class TslRefreshScheduler {

  private static final Logger log = LoggerFactory.getLogger(TslRefreshScheduler.class);

  private final TslService tslService;
  private final TslProperties props;

  public TslRefreshScheduler(TslService tslService, TslProperties props) {
    this.tslService = tslService;
    this.props = props;
  }

  @Scheduled(cron = "${app.tsl.refresh.cron}", zone = "${app.tsl.refresh.timezone}")
  @SchedulerLock(name = "tslRefresh", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void scheduledRefresh() {
    log.info("TSL scheduled refresh start");
    tslService.refresh(RefreshTrigger.SCHEDULED, null);
  }

  @EventListener
  public void onReady(ApplicationReadyEvent ev) {
    String mode = props.getRefresh().getStartupMode();
    switch (mode == null ? "BACKGROUND" : mode.toUpperCase()) {
      case "BLOCKING" -> tslService.refresh(RefreshTrigger.STARTUP, null);
      case "BACKGROUND" -> backgroundStartup();
      case "SKIP" -> log.info("TSL startup refresh skipped by config");
      default -> log.warn("Unknown TSL startup mode: {}", mode);
    }
  }

  @Async
  void backgroundStartup() {
    log.info("TSL background startup refresh start");
    tslService.refresh(RefreshTrigger.STARTUP, null);
  }
}
