package org.toresoft.signverify.application;

import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.config.TslProperties;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.RefreshTrigger;

@Component
public class TslRefreshScheduler {

  private static final Logger log = LoggerFactory.getLogger(TslRefreshScheduler.class);

  private final TslService tslService;
  private final TslProperties props;
  private final AuditService audit;
  private final ApplicationContext ctx;

  public TslRefreshScheduler(
      TslService tslService, TslProperties props, AuditService audit, ApplicationContext ctx) {
    this.tslService = tslService;
    this.props = props;
    this.audit = audit;
    this.ctx = ctx;
  }

  @Scheduled(cron = "${app.tsl.refresh.cron}", zone = "${app.tsl.refresh.timezone}")
  @SchedulerLock(name = "tslRefresh", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void scheduledRefresh() {
    log.info("TSL scheduled refresh start");
    try {
      var r = tslService.refresh(RefreshTrigger.SCHEDULED, null);
      audit.log(
          PrincipalType.SYSTEM,
          "scheduler-tsl-refresh",
          AuditActions.TSL_REFRESH,
          "tsl",
          r.getId().toString(),
          true,
          Map.of("trigger", "scheduled"));
    } catch (Exception e) {
      audit.log(
          PrincipalType.SYSTEM,
          "scheduler-tsl-refresh",
          AuditActions.TSL_REFRESH,
          "tsl",
          null,
          false,
          Map.of("trigger", "scheduled", "error", e.getMessage()));
      throw e;
    }
  }

  @EventListener
  public void onReady(ApplicationReadyEvent ev) {
    String mode = props.getRefresh().getStartupMode();
    switch (mode == null ? "BACKGROUND" : mode.toUpperCase()) {
      case "BLOCKING" -> {
        try {
          var r = tslService.refresh(RefreshTrigger.STARTUP, null);
          audit.log(
              PrincipalType.SYSTEM,
              "scheduler-tsl-refresh",
              AuditActions.TSL_REFRESH,
              "tsl",
              r.getId().toString(),
              true,
              Map.of("trigger", "startup"));
        } catch (Exception e) {
          audit.log(
              PrincipalType.SYSTEM,
              "scheduler-tsl-refresh",
              AuditActions.TSL_REFRESH,
              "tsl",
              null,
              false,
              Map.of("trigger", "startup", "error", e.getMessage()));
          throw e;
        }
      }
      case "BACKGROUND" ->
          // Route through the Spring proxy so @Async takes effect — a direct
          // this.backgroundStartup()
          // call would bypass the proxy and run synchronously on the startup thread.
          ctx.getBean(TslRefreshScheduler.class).backgroundStartup();
      case "SKIP" -> log.info("TSL startup refresh skipped by config");
      default -> log.warn("Unknown TSL startup mode: {}", mode);
    }
  }

  @Async
  public void backgroundStartup() {
    log.info("TSL background startup refresh start");
    try {
      var r = tslService.refresh(RefreshTrigger.STARTUP, null);
      audit.log(
          PrincipalType.SYSTEM,
          "scheduler-tsl-refresh",
          AuditActions.TSL_REFRESH,
          "tsl",
          r.getId().toString(),
          true,
          Map.of("trigger", "startup"));
    } catch (Exception e) {
      audit.log(
          PrincipalType.SYSTEM,
          "scheduler-tsl-refresh",
          AuditActions.TSL_REFRESH,
          "tsl",
          null,
          false,
          Map.of("trigger", "startup", "error", e.getMessage()));
      throw e;
    }
  }
}
