package org.toresoft.signverify.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.*;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class ValidationWorker {

  private static final Logger log = LoggerFactory.getLogger(ValidationWorker.class);

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final VerificationProfileService profileService;
  private final PolicyOverrideApplier overrideApplier;
  private final SignatureValidatorPort validator;
  private final ObjectMapper om;
  private final CircuitBreakerRegistry circuitRegistry;
  private final int maxPickupAttempts;

  public ValidationWorker(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      VerificationProfileService profileService,
      PolicyOverrideApplier overrideApplier,
      SignatureValidatorPort validator,
      ObjectMapper om,
      CircuitBreakerRegistry circuitRegistry,
      @Value("${app.async.max-pickup-attempts}") int maxPickupAttempts) {
    this.repo = repo;
    this.storage = storage;
    this.profileService = profileService;
    this.overrideApplier = overrideApplier;
    this.validator = validator;
    this.om = om;
    this.circuitRegistry = circuitRegistry;
    this.maxPickupAttempts = maxPickupAttempts;
  }

  @Scheduled(fixedDelayString = "${app.async.worker.poll-interval}")
  public void poll() {
    CircuitBreaker dssCircuit = circuitRegistry.circuitBreaker("dssValidator");
    if (dssCircuit.getState() == CircuitBreaker.State.OPEN) return;
    var jobs = repo.findPickablePending(maxPickupAttempts, PageRequest.of(0, 4));
    for (ValidationJob j : jobs) {
      try {
        process(j.getId());
      } catch (Exception e) {
        log.error("worker error on job {}", j.getId(), e);
      }
    }
  }

  @Transactional
  public void process(UUID id) {
    // Atomic claim: only the worker whose conditional update affects a row proceeds. In a
    // multi-instance deployment this guarantees a single owner per job, avoiding duplicate
    // validation and duplicate callbacks. The status flip and attempt increment happen in the DB.
    if (repo.claimForProcessing(id, Instant.now()) == 0) return;
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null) return;

    try {
      byte[] file = storage.read(job.getDocumentPath());
      var profile = profileService.getOrDefault(job.getProfileId());
      String policy = profile.getPolicyXml();
      if (job.getProfileOverrides() != null && !job.getProfileOverrides().isBlank()) {
        Map<String, Object> ov = om.readValue(job.getProfileOverrides(), new TypeReference<>() {});
        policy = overrideApplier.apply(policy, ov);
      }
      Set<ReportType> reports = parseReports(job.getReportsRequested());
      var result =
          validator.validate(
              new ValidationRequest(file, job.getDocumentFilename(), policy, reports));

      Map<String, Object> resultJson = new LinkedHashMap<>();
      resultJson.put("indication", result.indication());
      resultJson.put("subIndication", result.subIndication());
      resultJson.put("signatureFormat", result.signatureFormat());
      resultJson.put("signatureCount", result.signatureCount());
      resultJson.put("profileUsed", profile.getName());
      Map<String, Object> rep = new LinkedHashMap<>();
      for (var e : result.reportsJson().entrySet()) {
        rep.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
      }
      resultJson.put("reports", rep);

      String resultPath =
          storage.storeResult(job.getId().toString(), om.writeValueAsBytes(resultJson));
      job.setResultPath(resultPath);
      job.setStatus(JobStatus.COMPLETED);
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    } catch (AppException ae) {
      if ("dss.unavailable".equals(ae.getCode()) && job.getPickupAttempts() < maxPickupAttempts) {
        // Reset the in-progress marker set by the claim so the requeued job is not mistaken for a
        // running one by startedAt-based "in progress" queries / SLA metrics.
        job.setStartedAt(null);
        job.setStatus(JobStatus.PENDING);
        repo.save(job);
        return;
      }
      job.setStatus(JobStatus.FAILED);
      job.setErrorMessage(ae.getCode() + ": " + ae.getDetail());
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    } catch (Exception e) {
      job.setStatus(JobStatus.FAILED);
      job.setErrorMessage(e.getMessage());
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    }
  }

  private Set<ReportType> parseReports(String csv) {
    Set<ReportType> set = EnumSet.noneOf(ReportType.class);
    for (String s : csv.split(",")) {
      try {
        set.add(ReportType.valueOf(s.trim().toUpperCase()));
      } catch (IllegalArgumentException ignored) {
      }
    }
    if (set.isEmpty()) set.add(ReportType.SIMPLE);
    return set;
  }
}
