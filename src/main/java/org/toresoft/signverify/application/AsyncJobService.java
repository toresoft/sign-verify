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
package org.toresoft.signverify.application;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SecretCipherPort;
import org.toresoft.signverify.domain.port.SecretGeneratorPort;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

/**
 * Application service that submits asynchronous validation jobs.
 *
 * <p>Enforces global and per-principal backpressure limits, persists a {@link ValidationJob} in
 * {@code PENDING} state, and stores the input document via {@link DocumentStoragePort}. When a
 * callback URL is supplied, a fresh callback secret is generated via {@link SecretGeneratorPort}
 * and encrypted via {@link SecretCipherPort} before persistence; the plaintext is returned to the
 * caller in {@link SubmitResult} and never stored.
 */
@Service
public class AsyncJobService {

  private static final String DEFAULT_REPORTS = "simple,etsi";

  public record SubmitRequest(
      byte[] file,
      String filename,
      UUID profileId,
      String overridesJson,
      Set<ReportType> reports,
      String callbackUrl,
      String callbackAlgorithm) {}

  public record SubmitResult(UUID jobId, String callbackSecret) {}

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final SecretCipherPort cipher;
  private final SecretGeneratorPort secretGenerator;
  private final AuditService audit;
  private final int maxPerPrincipal;
  private final int maxGlobal;
  private final Duration jobTtl;
  private final String defaultAlgorithm;
  private final List<String> allowedAlgorithms;

  public AsyncJobService(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      SecretCipherPort cipher,
      SecretGeneratorPort secretGenerator,
      AuditService audit,
      @Value("${app.async.max-pending-per-principal}") int maxPerPrincipal,
      @Value("${app.async.max-pending-global}") int maxGlobal,
      @Value("${app.async.job-ttl}") Duration jobTtl,
      @Value("${app.callback.hmac-default-algorithm}") String defaultAlgorithm,
      @Value("${app.callback.allowed-algorithms}") List<String> allowedAlgorithms) {
    this.repo = repo;
    this.storage = storage;
    this.cipher = cipher;
    this.secretGenerator = secretGenerator;
    this.audit = audit;
    this.maxPerPrincipal = maxPerPrincipal;
    this.maxGlobal = maxGlobal;
    this.jobTtl = jobTtl;
    this.defaultAlgorithm = defaultAlgorithm;
    this.allowedAlgorithms = allowedAlgorithms;
  }

  /**
   * Submits a new async validation job. Enforces backpressure limits, stores the input, and
   * persists the job record.
   *
   * <p>The backpressure limits are <em>soft</em>: the count check and the insert are not atomic, so
   * concurrent submissions may briefly exceed {@code maxGlobal} / {@code maxPerPrincipal} by up to
   * the number of in-flight transactions. This is a deliberate trade-off — the limits protect
   * against sustained overload, not exact quota enforcement, and atomic enforcement would require
   * serializing submissions.
   *
   * @param req submission payload
   * @param actor principal performing the submission
   * @return the generated job id and the plaintext callback secret (null when no callbackUrl)
   * @throws AppException with backpressure semantics if either global or per-principal active-job
   *     limits are reached
   */
  @Transactional
  public SubmitResult submit(SubmitRequest req, Principal actor) {
    long globalActive = repo.countActiveGlobal();
    long principalActive = repo.countActiveByPrincipal(actor.type(), actor.id());
    if (globalActive >= maxGlobal) {
      throw new AppException(
          Errors.EXCESSIVE_LOAD_ASYNC,
          429,
          "global async backpressure",
          "global async backpressure");
    }
    if (principalActive >= maxPerPrincipal) {
      throw new AppException(
          Errors.EXCESSIVE_LOAD_ASYNC,
          429,
          "per-principal async backpressure",
          "per-principal async backpressure");
    }
    String callbackSecret = null;
    if (req.callbackUrl() != null) {
      validateCallbackUrl(req.callbackUrl());
      callbackSecret = secretGenerator.generateCallbackSecret();
    }
    // Enforce the HMAC algorithm allowlist at submit time: HmacSigner passes this value straight
    // to Mac.getInstance, so an unvetted value would either weaken the callback signature (e.g.
    // HmacMD5) or blow up only at dispatch time, after the validation work is done.
    if (req.callbackAlgorithm() != null
        && allowedAlgorithms.stream().noneMatch(a -> a.equalsIgnoreCase(req.callbackAlgorithm()))) {
      throw AppException.badRequest("callbackAlgorithm must be one of " + allowedAlgorithms);
    }

    UUID jobId = UUID.randomUUID();
    String docPath = storage.storeInput(jobId.toString(), req.filename(), req.file());

    ValidationJob j = new ValidationJob();
    j.setId(jobId);
    j.setStatus(JobStatus.PENDING);
    j.setProfileId(req.profileId());
    j.setProfileOverrides(req.overridesJson());
    j.setReportsRequested(joinReports(req.reports()));
    j.setDocumentPath(docPath);
    j.setDocumentFilename(req.filename());
    j.setCallbackUrl(req.callbackUrl());
    if (callbackSecret != null) {
      j.setCallbackSecretCipher(cipher.encrypt(callbackSecret));
    }
    j.setCallbackAlgorithm(
        req.callbackAlgorithm() == null ? defaultAlgorithm : req.callbackAlgorithm());

    Instant now = Instant.now();
    j.setCreatedAt(now);
    j.setExpiresAt(now.plus(jobTtl));
    j.setRequestedByPrincipalType(actor.type());
    j.setRequestedByPrincipalId(actor.id());
    repo.save(j);

    // Audit the successful submission. The plaintext file bytes, callback secret and HMAC
    // algorithm are intentionally excluded; the filename and profileId are sufficient to trace
    // the submission while staying within the audit log "no PII / no secrets" policy.
    Map<String, Object> details = new HashMap<>();
    details.put("filename", req.filename());
    if (req.profileId() != null) details.put("profileId", req.profileId().toString());
    audit.log(actor, AuditActions.JOB_SUBMIT, "job", jobId.toString(), true, details);

    return new SubmitResult(jobId, callbackSecret);
  }

  /** Fail fast on malformed callback URLs at submission instead of only at dispatch time. */
  private static void validateCallbackUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw AppException.badRequest("callbackUrl is not a valid URL");
    }
    String scheme = uri.getScheme();
    boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    if (!httpScheme || uri.getHost() == null) {
      throw AppException.badRequest("callbackUrl must be an absolute http(s) URL");
    }
  }

  private static String joinReports(Set<ReportType> reports) {
    if (reports == null || reports.isEmpty()) {
      return DEFAULT_REPORTS;
    }
    StringBuilder sb = new StringBuilder();
    if (reports.contains(ReportType.SIMPLE)) {
      sb.append("simple");
    }
    for (ReportType r : reports) {
      if (r == ReportType.SIMPLE) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(r.name().toLowerCase());
    }
    return sb.length() == 0 ? DEFAULT_REPORTS : sb.toString();
  }
}
