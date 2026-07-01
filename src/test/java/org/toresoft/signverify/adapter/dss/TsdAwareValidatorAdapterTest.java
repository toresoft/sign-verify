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
package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;

/**
 * Verifies that RFC 5544 TimeStampedData (.tsd) files — rejected by DSS {@code
 * SignedDocumentValidator.fromDocument()} — are routed through the Bouncy Castle unwrap path and
 * validated (inner CAdES signatures + wrapper RFC 3161 timestamps), instead of failing with {@code
 * signatureParseError}.
 */
@SpringBootTest
@ActiveProfiles("test")
class TsdAwareValidatorAdapterTest {

  @Autowired private SignatureValidatorPort validator;

  private static String basicPolicy() throws Exception {
    return new String(
        new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
        StandardCharsets.UTF_8);
  }

  private static byte[] sampleTsd() throws Exception {
    return new ClassPathResource("assets/tsd/sample-rfc5544.tsd").getInputStream().readAllBytes();
  }

  @Test
  void primary_validator_is_tsd_aware_decorator() {
    assertThat(validator).isInstanceOf(TsdAwareValidatorAdapter.class);
  }

  @Test
  void validates_rfc5544_tsd_instead_of_rejecting_it() throws Exception {
    ValidationResult result =
        validator.validate(
            new ValidationRequest(
                sampleTsd(), "sample-rfc5544.tsd", basicPolicy(), Set.of(ReportType.SIMPLE)));

    // The wrapper carries a FreeTSA RFC 3161 token over unsigned content: no inner signatures, but
    // the timestamp is validated. FreeTSA is not in any EU Trusted List, so the outcome is
    // INDETERMINATE (NO_CERTIFICATE_CHAIN_FOUND) rather than a hard parse failure.
    assertThat(result.signatureFormat()).isEqualTo("RFC5544_TSD");
    assertThat(result.signatureCount()).isZero();
    assertThat(result.indication()).isEqualTo("INDETERMINATE");
    assertThat(result.reportsJson()).containsKey(ReportType.SIMPLE);
  }

  @Test
  void garbage_input_still_fails_with_parse_error() throws Exception {
    byte[] bogus = "not a signature and not a tsd".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(
            () ->
                validator.validate(
                    new ValidationRequest(
                        bogus, "x.bin", basicPolicy(), Set.of(ReportType.SIMPLE))))
        .isInstanceOf(AppException.class);
  }

  @Test
  void tsd_response_lists_wrapper_timestamp() throws Exception {
    var result =
        validator.validate(
            new ValidationRequest(
                sampleTsd(), "sample-rfc5544.tsd", basicPolicy(), Set.of(ReportType.SIMPLE)));

    assertThat(result.timestamps()).isNotEmpty();
    assertThat(result.timestamps().get(0).indication()).isNotBlank();
    assertThat(result.signatures()).isEmpty(); // unsigned inner content
  }
}
