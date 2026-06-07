package org.toresoft.signverify.application;

import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.adapter.dss.TrustedCertificateMirror;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;
import org.toresoft.signverify.persistence.TslRefreshRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class TslService {

  private static final Logger log = LoggerFactory.getLogger(TslService.class);

  private final TLValidationJob job;
  private final TrustedListsCertificateSource tslSource;
  private final TrustedCertificateMirror mirror;
  private final TrustedCertificateRepository certRepo;
  private final TslRefreshRepository refreshRepo;
  private final AtomicBoolean ready = new AtomicBoolean(false);

  public TslService(
      TLValidationJob job,
      TrustedListsCertificateSource tslSource,
      TrustedCertificateMirror mirror,
      TrustedCertificateRepository certRepo,
      TslRefreshRepository refreshRepo) {
    this.job = job;
    this.tslSource = tslSource;
    this.mirror = mirror;
    this.certRepo = certRepo;
    this.refreshRepo = refreshRepo;
  }

  public boolean isReady() {
    return ready.get();
  }

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

  public Optional<TslRefresh> getLastRefresh() {
    return refreshRepo.findTopByOrderByStartedAtDesc();
  }

  public long getCertificateCount() {
    return certRepo.count();
  }

  public Page<TrustedCertificate> listCertificates(
      String ski,
      String aki,
      String subjectCn,
      String subjectDn,
      String issuerCn,
      String issuerDn,
      String country,
      String tspName,
      String tspServiceType,
      String tspServiceStatus,
      String serialNumber,
      OffsetDateTime validAt,
      boolean includeRemoved,
      int page,
      int size) {

    Specification<TrustedCertificate> spec =
        (root, q, cb) -> {
          List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
          if (!includeRemoved) ps.add(cb.isNull(root.get("removedAt")));
          if (ski != null) ps.add(cb.equal(root.get("ski"), ski));
          if (aki != null) ps.add(cb.equal(root.get("aki"), aki));
          if (subjectCn != null)
            ps.add(cb.like(cb.lower(root.get("subjectCn")), "%" + subjectCn.toLowerCase() + "%"));
          if (subjectDn != null)
            ps.add(cb.like(cb.lower(root.get("subjectDn")), "%" + subjectDn.toLowerCase() + "%"));
          if (issuerCn != null)
            ps.add(cb.like(cb.lower(root.get("issuerCn")), "%" + issuerCn.toLowerCase() + "%"));
          if (issuerDn != null)
            ps.add(cb.like(cb.lower(root.get("issuerDn")), "%" + issuerDn.toLowerCase() + "%"));
          if (country != null) ps.add(cb.equal(root.get("country"), country));
          if (tspName != null)
            ps.add(cb.like(cb.lower(root.get("tspName")), "%" + tspName.toLowerCase() + "%"));
          if (tspServiceType != null) ps.add(cb.equal(root.get("tspServiceType"), tspServiceType));
          if (tspServiceStatus != null)
            ps.add(cb.equal(root.get("tspServiceStatus"), tspServiceStatus));
          if (serialNumber != null) ps.add(cb.equal(root.get("serialNumber"), serialNumber));
          if (validAt != null) {
            Instant at = validAt.toInstant();
            ps.add(cb.lessThanOrEqualTo(root.get("validFrom"), at));
            ps.add(cb.greaterThanOrEqualTo(root.get("validTo"), at));
          }
          return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

    return certRepo.findAll(spec, PageRequest.of(page, size));
  }

  public TrustedCertificate getCertificate(UUID id) {
    return certRepo
        .findById(id)
        .orElseThrow(
            () ->
                org.toresoft.signverify.domain.exception.AppException.notFound(
                    "certificate not found"));
  }

  public Map<String, Object> certToMap(TrustedCertificate c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("ski", c.getSki());
    m.put("aki", c.getAki());
    m.put("subjectDn", c.getSubjectDn());
    m.put("subjectCn", c.getSubjectCn());
    m.put("issuerDn", c.getIssuerDn());
    m.put("issuerCn", c.getIssuerCn());
    m.put("serialNumber", c.getSerialNumber());
    m.put("country", c.getCountry());
    m.put("tspName", c.getTspName());
    m.put("tspServiceType", c.getTspServiceType());
    m.put("tspServiceStatus", c.getTspServiceStatus());
    m.put("validFrom", c.getValidFrom() == null ? null : c.getValidFrom().atOffset(ZoneOffset.UTC));
    m.put("validTo", c.getValidTo() == null ? null : c.getValidTo().atOffset(ZoneOffset.UTC));
    m.put("lastSeenAt", c.getLastSeenAt().atOffset(ZoneOffset.UTC));
    m.put("removedAt", c.getRemovedAt() == null ? null : c.getRemovedAt().atOffset(ZoneOffset.UTC));
    m.put("certificateDerB64", c.getCertificateDerB64());
    m.put("tslUrl", c.getTslUrl());
    return m;
  }

  public Map<String, Object> refreshToMap(TslRefresh r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("trigger", r.getTrigger());
    m.put("startedAt", r.getStartedAt().atOffset(ZoneOffset.UTC));
    if (r.getCompletedAt() != null)
      m.put("completedAt", r.getCompletedAt().atOffset(ZoneOffset.UTC));
    m.put("status", r.getStatus());
    m.put("sourcesTotal", r.getSourcesTotal());
    m.put("sourcesFailed", r.getSourcesFailed());
    m.put("certificatesAdded", r.getCertificatesAdded());
    m.put("certificatesRemoved", r.getCertificatesRemoved());
    m.put("certificatesUnchanged", r.getCertificatesUnchanged());
    return m;
  }
}
