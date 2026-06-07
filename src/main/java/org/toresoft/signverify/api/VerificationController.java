package org.toresoft.signverify.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationController {

  private final VerificationService service;
  private final ObjectMapper om;

  public VerificationController(VerificationService service, ObjectMapper om) {
    this.service = service;
    this.om = om;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> verify(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    Metadata m = parseMetadata(metadataJson);
    Set<ReportType> reports = parseReports(m.reports());
    UUID profileId = m.profileId() == null ? null : UUID.fromString(m.profileId());

    var req =
        new VerificationService.VerifyRequest(
            file.getBytes(), file.getOriginalFilename(), profileId, m.profileOverrides(), reports);
    var res = service.verifySync(req);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("verifiedAt", OffsetDateTime.now().toString());
    out.put("profileUsed", res.profileName());
    out.put("overridesApplied", res.overridesApplied());
    out.put("signatureFormat", res.result().signatureFormat());
    out.put("indication", res.result().indication());
    out.put("subIndication", res.result().subIndication());
    out.put("signatureCount", res.result().signatureCount());
    Map<String, Object> reportsOut = new LinkedHashMap<>();
    for (var e : res.result().reportsJson().entrySet()) {
      reportsOut.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
    }
    out.put("reports", reportsOut);
    return ResponseEntity.ok(out);
  }

  public record Metadata(
      String profileId, Map<String, Object> profileOverrides, List<String> reports) {}

  private Metadata parseMetadata(String json) {
    if (json == null || json.isBlank())
      return new Metadata(null, Map.of(), List.of("simple", "etsi"));
    try {
      return om.readValue(json, new TypeReference<Metadata>() {});
    } catch (Exception e) {
      throw AppException.badRequest("invalid metadata json");
    }
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
