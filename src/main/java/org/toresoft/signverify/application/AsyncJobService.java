package org.toresoft.signverify.application;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

/**
 * Application service that submits asynchronous validation jobs.
 *
 * <p>Enforces global and per-principal backpressure limits, persists a {@link ValidationJob} in
 * {@code PENDING} state, and stores the input document via {@link DocumentStoragePort}. The
 * callback secret, if provided, is encrypted via {@link SecretCipherPort} before persistence.
 */
@Service
public class AsyncJobService {

  private static final String DEFAULT_ALGORITHM = "HmacSHA256";
  private static final String DEFAULT_REPORTS = "simple,etsi";

  public record SubmitRequest(
      byte[] file,
      String filename,
      UUID profileId,
      String overridesJson,
      Set<ReportType> reports,
      String callbackUrl,
      String callbackSecret,
      String callbackAlgorithm) {}

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final SecretCipherPort cipher;
  private final int maxPerPrincipal;
  private final int maxGlobal;
  private final Duration jobTtl;

  public AsyncJobService(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      SecretCipherPort cipher,
      @Value("${app.async.max-pending-per-principal}") int maxPerPrincipal,
      @Value("${app.async.max-pending-global}") int maxGlobal,
      @Value("${app.async.job-ttl}") Duration jobTtl) {
    this.repo = repo;
    this.storage = storage;
    this.cipher = cipher;
    this.maxPerPrincipal = maxPerPrincipal;
    this.maxGlobal = maxGlobal;
    this.jobTtl = jobTtl;
  }

  /**
   * Submits a new async validation job. Enforces backpressure limits, stores the input, and
   * persists the job record.
   *
   * @param req submission payload
   * @param actor principal performing the submission
   * @return the generated job id
   * @throws AppException with backpressure semantics if either global or per-principal active-job
   *     limits are reached
   */
  @Transactional
  public UUID submit(SubmitRequest req, Principal actor) {
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
    if (req.callbackUrl() != null) {
      validateCallbackUrl(req.callbackUrl());
      if (req.callbackSecret() == null || req.callbackSecret().isBlank()) {
        throw AppException.badRequest("callbackSecret is required when callbackUrl is provided");
      }
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
    if (req.callbackSecret() != null) {
      j.setCallbackSecretCipher(cipher.encrypt(req.callbackSecret()));
    }
    j.setCallbackAlgorithm(
        req.callbackAlgorithm() == null ? DEFAULT_ALGORITHM : req.callbackAlgorithm());

    Instant now = Instant.now();
    j.setCreatedAt(now);
    j.setExpiresAt(now.plus(jobTtl));
    j.setRequestedByPrincipalType(actor.type());
    j.setRequestedByPrincipalId(actor.id());
    repo.save(j);
    return jobId;
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
