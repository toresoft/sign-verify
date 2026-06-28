package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationResult;

class VerificationServiceTest {

  @Test
  void rejects_with_503_when_concurrency_full() throws Exception {
    SignatureValidatorPort validator = Mockito.mock(SignatureValidatorPort.class);
    VerificationProfileService profileService = Mockito.mock(VerificationProfileService.class);
    PolicyOverrideApplier applier = new PolicyOverrideApplier();

    VerificationProfile p = new VerificationProfile();
    p.setName("STANDARD");
    p.setPreset(ProfilePreset.STANDARD);
    p.setPolicyXml(
        "<ConstraintsParameters xmlns=\"http://dss.esig.europa.eu/validation/policy\"/>");
    when(profileService.getOrDefault(any())).thenReturn(p);
    when(validator.validate(any()))
        .thenAnswer(
            inv -> {
              Thread.sleep(3000);
              return new ValidationResult(
                  "PAdES", "TOTAL_PASSED", null, 1, Map.of(), List.of(), List.of());
            });

    VerificationService service = new VerificationService(validator, profileService, applier, 1);

    Thread t1 =
        new Thread(
            () ->
                service.verifySync(
                    new VerificationService.VerifyRequest(
                        new byte[] {1}, "a.pdf", null, Map.of(), Set.of(ReportType.SIMPLE))));
    t1.start();
    Thread.sleep(200);

    assertThatThrownBy(
            () ->
                service.verifySync(
                    new VerificationService.VerifyRequest(
                        new byte[] {2}, "b.pdf", null, Map.of(), Set.of(ReportType.SIMPLE))))
        .isInstanceOf(AppException.class);
    t1.join();
  }
}
