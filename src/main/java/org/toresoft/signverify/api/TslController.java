package org.toresoft.signverify.api;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.toresoft.signverify.application.AuditActions;
import org.toresoft.signverify.application.AuditService;
import org.toresoft.signverify.application.TslService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.RefreshTrigger;
import org.toresoft.signverify.domain.model.TrustedCertificate;
import org.toresoft.signverify.security.Principal;

@RestController
@RequestMapping("/api/v1/tsl")
public class TslController {

  private static final int MAX_PAGE_SIZE = 100;

  private final TslService tslService;
  private final AuditService audit;

  public TslController(TslService tslService, AuditService audit) {
    this.tslService = tslService;
    this.audit = audit;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    tslService.getLastRefresh().ifPresent(r -> out.put("lastRefresh", tslService.refreshToMap(r)));
    out.put("currentCertificateCount", tslService.getCertificateCount());
    out.put("ready", tslService.isReady());
    return out;
  }

  @PostMapping("/refresh")
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<Map<String, Object>> forceRefresh() {
    Principal actor =
        (Principal)
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    var r = tslService.refresh(RefreshTrigger.MANUAL, actor);

    // Record the manual refresh so operators can correlate with the request and distinguish it
    // from the scheduled / startup refresh entries recorded by TslRefreshScheduler.
    audit.log(
        actor,
        AuditActions.TSL_REFRESH,
        "tsl",
        r.getId().toString(),
        true,
        Map.of("trigger", RefreshTrigger.MANUAL.name().toLowerCase()));

    return ResponseEntity.accepted()
        .body(Map.of("refreshId", r.getId(), "status", r.getStatus().name()));
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

    if (page < 0) {
      throw AppException.badRequest("page must be >= 0");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw AppException.badRequest("size must be between 1 and " + MAX_PAGE_SIZE);
    }

    Page<TrustedCertificate> result =
        tslService.listCertificates(
            ski,
            aki,
            subjectCn,
            subjectDn,
            issuerCn,
            issuerDn,
            country,
            tspName,
            tspServiceType,
            tspServiceStatus,
            serialNumber,
            validAt,
            includeRemoved,
            page,
            size);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("page", result.getNumber());
    out.put("size", result.getSize());
    out.put("totalElements", result.getTotalElements());
    out.put("totalPages", result.getTotalPages());
    out.put("content", result.map(tslService::certToMap).toList());
    return out;
  }

  @GetMapping("/certificates/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    return tslService.certToMap(tslService.getCertificate(id));
  }
}
