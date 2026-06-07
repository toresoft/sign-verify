package org.toresoft.signverify.domain.port;

public interface SignatureValidatorPort {
  ValidationResult validate(ValidationRequest req);
}
