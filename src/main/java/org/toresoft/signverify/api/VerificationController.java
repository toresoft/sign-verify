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
package org.toresoft.signverify.api;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.toresoft.signverify.application.VerificationService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationController {

  private final VerificationService service;
  private final JsonMapper om;

  public VerificationController(VerificationService service, JsonMapper om) {
    this.service = service;
    this.om = om;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> verify(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "profileId", required = false) String profileId,
      @RequestPart(value = "profileOverrides", required = false) String profileOverrides,
      @RequestPart(value = "reports", required = false) String reports,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    var parts =
        VerificationRequestParts.from(
            om, metadataJson, profileId, profileOverrides, reports, null, null, null, null);

    UUID pid = parts.profileId() == null ? null : UUID.fromString(parts.profileId());
    Map<String, Object> overrides =
        VerificationRequestParts.parseOverrides(om, parts.profileOverridesJson());
    if (overrides == null) {
      overrides = Map.of();
    }
    Set<ReportType> reportTypes = parseReports(parts.reports());

    var req =
        new VerificationService.VerifyRequest(
            file.getBytes(), file.getOriginalFilename(), pid, overrides, reportTypes);
    var res = service.verifySync(req);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("verifiedAt", OffsetDateTime.now().toString());
    out.put("profileUsed", res.profileName());
    out.put("overridesApplied", res.overridesApplied());
    out.put("signatureFormat", res.result().signatureFormat());
    out.put("indication", res.result().indication());
    out.put("subIndication", res.result().subIndication());
    out.put("signatureCount", res.result().signatureCount());
    out.put("signatures", res.result().signatures());
    out.put("timestamps", res.result().timestamps());
    Map<String, Object> reportsOut = new LinkedHashMap<>();
    for (var e : res.result().reportsJson().entrySet()) {
      reportsOut.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
    }
    out.put("reports", reportsOut);
    return ResponseEntity.ok(out);
  }

  private Set<ReportType> parseReports(List<String> raw) {
    if (raw == null || raw.isEmpty()) return EnumSet.of(ReportType.SIMPLE, ReportType.ETSI);
    EnumSet<ReportType> set = EnumSet.noneOf(ReportType.class);
    for (String s : raw) {
      switch (s.toLowerCase()) {
        case "simple" -> set.add(ReportType.SIMPLE);
        case "detailed" -> set.add(ReportType.DETAILED);
        case "diagnostic" -> set.add(ReportType.DIAGNOSTIC);
        case "etsi" -> set.add(ReportType.ETSI);
        default -> throw AppException.badRequest("unknown report type: " + s);
      }
    }
    return set;
  }
}
