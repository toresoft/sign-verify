package org.toresoft.signverify.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.toresoft.signverify.application.AsyncJobService;
import org.toresoft.signverify.application.AuditActions;
import org.toresoft.signverify.application.AuditService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@RestController
@RequestMapping("/api/v1/verifications")
public class AsyncVerificationController {

  private final AsyncJobService asyncService;
  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final ObjectMapper om;
  private final AuditService audit;

  public AsyncVerificationController(
      AsyncJobService s,
      ValidationJobRepository r,
      DocumentStoragePort st,
      ObjectMapper om,
      AuditService audit) {
    this.asyncService = s;
    this.repo = r;
    this.storage = st;
    this.om = om;
    this.audit = audit;
  }

  @PostMapping(value = "/async", consumes = "multipart/form-data")
  public ResponseEntity<Map<String, Object>> submit(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    Map<String, Object> meta =
        (metadataJson == null || metadataJson.isBlank())
            ? Map.of()
            : om.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
    UUID profileId =
        meta.get("profileId") == null
            ? null
            : UUID.fromString(String.valueOf(meta.get("profileId")));
    String overridesJson =
        meta.get("profileOverrides") == null
            ? null
            : om.writeValueAsString(meta.get("profileOverrides"));
    @SuppressWarnings("unchecked")
    List<String> reports = (List<String>) meta.getOrDefault("reports", List.of("simple", "etsi"));
    Set<ReportType> reportTypes = EnumSet.noneOf(ReportType.class);
    for (String r : reports) {
      try {
        reportTypes.add(ReportType.valueOf(r.toUpperCase()));
      } catch (Exception ignored) {
      }
    }
    if (reportTypes.isEmpty()) reportTypes.add(ReportType.SIMPLE);
    String callbackUrl = (String) meta.get("callbackUrl");
    String callbackSecret = (String) meta.get("callbackSecret");
    String callbackAlgo = (String) meta.get("callbackAlgorithm");

    Principal actor =
        (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    UUID jobId =
        asyncService.submit(
            new AsyncJobService.SubmitRequest(
                file.getBytes(),
                file.getOriginalFilename(),
                profileId,
                overridesJson,
                reportTypes,
                callbackUrl,
                callbackSecret,
                callbackAlgo),
            actor);

    return ResponseEntity.status(202)
        .header("Location", "/api/v1/verifications/jobs/" + jobId)
        .body(Map.of("jobId", jobId, "status", "PENDING"));
  }

  @GetMapping("/jobs/{jobId}")
  public ResponseEntity<?> getJob(@PathVariable UUID jobId) throws Exception {
    ValidationJob job =
        repo.findById(jobId).orElseThrow(() -> AppException.notFound("job not found"));
    Principal actor =
        (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    boolean isOwner =
        actor.type() == job.getRequestedByPrincipalType()
            && actor.id().equals(job.getRequestedByPrincipalId());
    boolean isPriv = actor.role() == Role.PRIVILEGED;
    if (!isOwner && !isPriv) {
      // The caller is neither the owner nor a privileged operator. Record the access denial
      // as a failed audit event before the 404 so security monitoring can detect probing.
      audit.log(
          actor,
          AuditActions.AUTH_DENIED,
          "job",
          job.getId().toString(),
          false,
          Map.of("reason", "not-owner"));
      throw AppException.notFound("job not found");
    }

    job.setLastAccessedAt(Instant.now());
    repo.save(job);

    if (job.getStatus() == JobStatus.DELETED) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("jobId", job.getId());
      body.put("originalStatus", job.getOriginalStatus());
      body.put(
          "completedAt",
          job.getCompletedAt() == null ? null : job.getCompletedAt().atOffset(ZoneOffset.UTC));
      body.put(
          "deletedAt",
          job.getDeletedAt() == null ? null : job.getDeletedAt().atOffset(ZoneOffset.UTC));
      body.put("message", "result no longer available");
      return ResponseEntity.status(410).body(body);
    }
    return ResponseEntity.ok(buildResponse(job));
  }

  private Object buildResponse(ValidationJob job) throws Exception {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("jobId", job.getId());
    out.put("status", job.getStatus().name());
    out.put("createdAt", job.getCreatedAt().atOffset(ZoneOffset.UTC));
    if (job.getStartedAt() != null)
      out.put("startedAt", job.getStartedAt().atOffset(ZoneOffset.UTC));
    if (job.getCompletedAt() != null)
      out.put("completedAt", job.getCompletedAt().atOffset(ZoneOffset.UTC));
    if (job.getDeliveredAt() != null)
      out.put("deliveredAt", job.getDeliveredAt().atOffset(ZoneOffset.UTC));
    out.put("expiresAt", job.getExpiresAt().atOffset(ZoneOffset.UTC));
    out.put("callbackAttempts", job.getCallbackAttempts());
    if (job.getResultPath() != null) {
      out.put("result", om.readTree(storage.read(job.getResultPath())));
    } else if (job.getErrorMessage() != null) {
      out.put("error", job.getErrorMessage());
    }
    if (job.getLastCallbackError() != null)
      out.put("lastCallbackError", job.getLastCallbackError());
    return out;
  }
}
