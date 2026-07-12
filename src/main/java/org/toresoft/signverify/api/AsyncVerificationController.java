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
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/verifications")
public class AsyncVerificationController {

  private final AsyncJobService asyncService;
  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final JsonMapper om;
  private final AuditService audit;

  public AsyncVerificationController(
      AsyncJobService s,
      ValidationJobRepository r,
      DocumentStoragePort st,
      JsonMapper om,
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
      @RequestPart(value = "profileId", required = false) String profileId,
      @RequestPart(value = "profileOverrides", required = false) String profileOverrides,
      @RequestPart(value = "reports", required = false) String reports,
      @RequestPart(value = "callbackUrl", required = false) String callbackUrl,
      @RequestPart(value = "callbackSecret", required = false) String callbackSecret,
      @RequestPart(value = "callbackAlgorithm", required = false) String callbackAlgorithm,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    var parts =
        VerificationRequestParts.from(
            om,
            metadataJson,
            profileId,
            profileOverrides,
            reports,
            callbackUrl,
            callbackSecret,
            callbackAlgorithm,
            null);

    UUID pid = parts.profileId() == null ? null : UUID.fromString(parts.profileId());
    Map<String, Object> overrides =
        VerificationRequestParts.parseOverrides(om, parts.profileOverridesJson());
    String overridesJson = overrides == null ? null : om.writeValueAsString(overrides);

    List<String> reportList = parts.reports() == null ? List.of("simple", "etsi") : parts.reports();
    Set<ReportType> reportTypes = EnumSet.noneOf(ReportType.class);
    for (String r : reportList) {
      try {
        reportTypes.add(ReportType.valueOf(r.toUpperCase()));
      } catch (Exception ignored) {
      }
    }
    if (reportTypes.isEmpty()) {
      reportTypes.add(ReportType.SIMPLE);
    }

    Principal actor =
        (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    UUID jobId =
        asyncService.submit(
            new AsyncJobService.SubmitRequest(
                file.getBytes(),
                file.getOriginalFilename(),
                pid,
                overridesJson,
                reportTypes,
                parts.callbackUrl(),
                parts.callbackSecret(),
                parts.callbackAlgorithm()),
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
    } else if (job.getErrorCode() != null || job.getErrorMessage() != null) {
      out.put("errorCode", job.getErrorCode() == null ? Errors.INTERNAL : job.getErrorCode());
      if (job.getErrorMessage() != null) out.put("error", job.getErrorMessage());
    }
    if (job.getLastCallbackError() != null)
      out.put("lastCallbackError", job.getLastCallbackError());
    return out;
  }
}
