package org.toresoft.signverify.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;

@Service
public class VerificationService {

  public record VerifyRequest(
      byte[] file,
      String filename,
      UUID profileId,
      Map<String, Object> overrides,
      Set<ReportType> reports) {}

  public record VerifyResponse(
      String profileName, boolean overridesApplied, ValidationResult result) {}

  private final SignatureValidatorPort validator;
  private final VerificationProfileService profileService;
  private final PolicyOverrideApplier overrideApplier;
  private final Semaphore concurrencyLimiter;

  public VerificationService(
      SignatureValidatorPort validator,
      VerificationProfileService profileService,
      PolicyOverrideApplier overrideApplier,
      @Value("${app.verify.max-concurrent}") int maxConcurrent) {
    this.validator = validator;
    this.profileService = profileService;
    this.overrideApplier = overrideApplier;
    this.concurrencyLimiter = new Semaphore(maxConcurrent);
  }

  public VerifyResponse verifySync(VerifyRequest req) {
    boolean acquired;
    try {
      acquired = concurrencyLimiter.tryAcquire(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw AppException.concurrency("interrupted");
    }
    if (!acquired) throw AppException.concurrency("verify concurrency limit reached");

    try {
      var profile = profileService.getOrDefault(req.profileId());
      String policyXml = profile.getPolicyXml();
      boolean overridesApplied = req.overrides() != null && !req.overrides().isEmpty();
      if (overridesApplied) policyXml = overrideApplier.apply(policyXml, req.overrides());
      var result =
          validator.validate(
              new ValidationRequest(req.file(), req.filename(), policyXml, req.reports()));
      return new VerifyResponse(profile.getName(), overridesApplied, result);
    } finally {
      concurrencyLimiter.release();
    }
  }
}
