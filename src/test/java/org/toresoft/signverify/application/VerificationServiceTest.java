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
