package org.toresoft.signverify.api;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.toresoft.signverify.application.TslService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.RefreshTrigger;
import org.toresoft.signverify.domain.model.TrustedCertificate;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;
import org.toresoft.signverify.persistence.TslRefreshRepository;
import org.toresoft.signverify.security.Principal;

@RestController
@RequestMapping("/api/v1/tsl")
public class TslController {

  private final TslService tslService;
  private final TrustedCertificateRepository certRepo;
  private final TslRefreshRepository refreshRepo;

  public TslController(TslService s, TrustedCertificateRepository c, TslRefreshRepository r) {
    this.tslService = s; this.certRepo = c; this.refreshRepo = r;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    refreshRepo.findTopByOrderByStartedAtDesc().ifPresent(r -> out.put("lastRefresh", refreshToMap(r)));
    out.put("currentCertificateCount", certRepo.count());
    out.put("ready", tslService.isReady());
    return out;
  }

  @PostMapping("/refresh")
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<Map<String, Object>> forceRefresh() {
    Principal actor = (Principal) org.springframework.security.core.context.SecurityContextHolder
        .getContext().getAuthentication().getPrincipal();
    var r = tslService.refresh(RefreshTrigger.MANUAL, actor);
    return ResponseEntity.accepted().body(Map.of("refreshId", r.getId(), "status", r.getStatus().name()));
  }

  @GetMapping("/certificates")
  public Map<String, Object> list(
      @RequestParam(required = false) String ski,
      @RequestParam(required = false) String aki,
      @RequestParam(required = false) String subjectCn,
      @RequestParam(required = false) String subjectDn,
      @RequestParam(required = false) String issuerCn,
      @RequestParam(required = false) String issuerDn,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String tspName,
      @RequestParam(required = false) String tspServiceType,
      @RequestParam(required = false) String tspServiceStatus,
      @RequestParam(required = false) String serialNumber,
      @RequestParam(required = false) OffsetDateTime validAt,
      @RequestParam(defaultValue = "false") boolean includeRemoved,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    Specification<TrustedCertificate> spec = (root, q, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      if (!includeRemoved) ps.add(cb.isNull(root.get("removedAt")));
      if (ski != null) ps.add(cb.equal(root.get("ski"), ski));
      if (aki != null) ps.add(cb.equal(root.get("aki"), aki));
      if (subjectCn != null) ps.add(cb.like(cb.lower(root.get("subjectCn")), "%" + subjectCn.toLowerCase() + "%"));
      if (subjectDn != null) ps.add(cb.like(cb.lower(root.get("subjectDn")), "%" + subjectDn.toLowerCase() + "%"));
      if (issuerCn != null) ps.add(cb.like(cb.lower(root.get("issuerCn")), "%" + issuerCn.toLowerCase() + "%"));
      if (issuerDn != null) ps.add(cb.like(cb.lower(root.get("issuerDn")), "%" + issuerDn.toLowerCase() + "%"));
      if (country != null) ps.add(cb.equal(root.get("country"), country));
      if (tspName != null) ps.add(cb.like(cb.lower(root.get("tspName")), "%" + tspName.toLowerCase() + "%"));
      if (tspServiceType != null) ps.add(cb.equal(root.get("tspServiceType"), tspServiceType));
      if (tspServiceStatus != null) ps.add(cb.equal(root.get("tspServiceStatus"), tspServiceStatus));
      if (serialNumber != null) ps.add(cb.equal(root.get("serialNumber"), serialNumber));
      if (validAt != null) {
        Instant at = validAt.toInstant();
        ps.add(cb.lessThanOrEqualTo(root.get("validFrom"), at));
        ps.add(cb.greaterThanOrEqualTo(root.get("validTo"), at));
      }
      return cb.and(ps.toArray(new Predicate[0]));
    };

    var result = certRepo.findAll(spec, PageRequest.of(page, size));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("page", result.getNumber());
    out.put("size", result.getSize());
    out.put("totalElements", result.getTotalElements());
    out.put("totalPages", result.getTotalPages());
    out.put("content", result.map(this::certToMap).toList());
    return out;
  }

  @GetMapping("/certificates/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    var c = certRepo.findById(id).orElseThrow(() -> AppException.notFound("certificate not found"));
    return certToMap(c);
  }

  private Map<String, Object> certToMap(TrustedCertificate c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("ski", c.getSki()); m.put("aki", c.getAki());
    m.put("subjectDn", c.getSubjectDn()); m.put("subjectCn", c.getSubjectCn());
    m.put("issuerDn", c.getIssuerDn()); m.put("issuerCn", c.getIssuerCn());
    m.put("serialNumber", c.getSerialNumber()); m.put("country", c.getCountry());
    m.put("tspName", c.getTspName()); m.put("tspServiceType", c.getTspServiceType()); m.put("tspServiceStatus", c.getTspServiceStatus());
    m.put("validFrom", c.getValidFrom() == null ? null : c.getValidFrom().atOffset(ZoneOffset.UTC));
    m.put("validTo", c.getValidTo() == null ? null : c.getValidTo().atOffset(ZoneOffset.UTC));
    m.put("lastSeenAt", c.getLastSeenAt().atOffset(ZoneOffset.UTC));
    m.put("removedAt", c.getRemovedAt() == null ? null : c.getRemovedAt().atOffset(ZoneOffset.UTC));
    m.put("certificateDerB64", c.getCertificateDerB64());
    m.put("tslUrl", c.getTslUrl());
    return m;
  }

  private Map<String, Object> refreshToMap(org.toresoft.signverify.domain.model.TslRefresh r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("trigger", r.getTrigger());
    m.put("startedAt", r.getStartedAt().atOffset(ZoneOffset.UTC));
    if (r.getCompletedAt() != null) m.put("completedAt", r.getCompletedAt().atOffset(ZoneOffset.UTC));
    m.put("status", r.getStatus());
    m.put("sourcesTotal", r.getSourcesTotal());
    m.put("sourcesFailed", r.getSourcesFailed());
    m.put("certificatesAdded", r.getCertificatesAdded());
    m.put("certificatesRemoved", r.getCertificatesRemoved());
    m.put("certificatesUnchanged", r.getCertificatesUnchanged());
    return m;
  }
}
