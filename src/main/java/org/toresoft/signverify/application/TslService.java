package org.toresoft.signverify.application;

import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.persistence.TslRefreshRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class TslService {

  private static final Logger log = LoggerFactory.getLogger(TslService.class);

  private final TLValidationJob job;
  private final TrustedListsCertificateSource tslSource;
  private final TrustedCertificateMirror mirror;
  private final TslRefreshRepository refreshRepo;
  private final AtomicBoolean ready = new AtomicBoolean(false);

  public TslService(TLValidationJob job, TrustedListsCertificateSource tslSource,
                    TrustedCertificateMirror mirror, TslRefreshRepository refreshRepo) {
    this.job = job;
    this.tslSource = tslSource;
    this.mirror = mirror;
    this.refreshRepo = refreshRepo;
  }

  public boolean isReady() { return ready.get(); }

  public TslRefresh refresh(RefreshTrigger trigger, Principal triggeredBy) {
    TslRefresh r = new TslRefresh();
    r.setId(UUID.randomUUID());
    r.setTrigger(trigger);
    r.setStartedAt(Instant.now());
    r.setStatus(RefreshStatus.RUNNING);
    if (triggeredBy != null) {
      r.setTriggeredByPrincipalType(triggeredBy.type());
      r.setTriggeredByPrincipalId(triggeredBy.id());
    }
    refreshRepo.save(r);

    try {
      job.onlineRefresh();
      var diff = mirror.sync(tslSource);
      r.setStatus(RefreshStatus.SUCCESS);
      r.setCertificatesAdded(diff.added());
      r.setCertificatesRemoved(diff.removed());
      r.setCertificatesUnchanged(diff.unchanged());
      ready.set(true);
    } catch (Exception e) {
      log.error("TSL refresh failed", e);
      r.setStatus(RefreshStatus.FAILED);
      r.setErrorSummary(e.getMessage());
    } finally {
      r.setCompletedAt(Instant.now());
      refreshRepo.save(r);
    }
    return r;
  }
}
