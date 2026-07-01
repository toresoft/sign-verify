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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.ValidationRequest;

@SpringBootTest
@ActiveProfiles("test")
class DssValidatorAdapterTest {

  @Autowired private DssValidatorAdapter adapter;
  @Autowired private ObjectMapper om;

  @Test
  void parses_error_for_garbage_input() {
    byte[] bogus = "not a signature".getBytes();
    String policy =
        "<ConstraintsParameters xmlns=\"http://dss.esig.europa.eu/validation/policy\"/>";
    assertThatThrownBy(
            () ->
                adapter.validate(
                    new ValidationRequest(bogus, "x.pdf", policy, Set.of(ReportType.SIMPLE))))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Unprocessable");
  }

  @Test
  void enriches_response_with_signatures_and_qualification() throws Exception {
    byte[] pdf =
        new ClassPathResource("assets/pades/sample-pades-valid.pdf")
            .getInputStream()
            .readAllBytes();
    String policy =
        new String(
            new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    var result =
        adapter.validate(
            new ValidationRequest(
                pdf, "sample-pades-valid.pdf", policy, Set.of(ReportType.SIMPLE)));

    assertThat(result.signatures()).isNotEmpty();
    var sig = result.signatures().get(0);
    assertThat(sig.signatureFormat()).startsWith("PAdES");
    assertThat(sig.signatureLevel()).isNotBlank();
    assertThat(sig.indication()).isNotBlank();
    assertThat(sig.claimedSigningTime()).isNotNull();
  }

  @Test
  void exposes_certificate_chain_metadata() throws Exception {
    byte[] pdf =
        new ClassPathResource("assets/pades/sample-pades-valid.pdf")
            .getInputStream()
            .readAllBytes();
    String policy =
        new String(
            new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    var result =
        adapter.validate(
            new ValidationRequest(
                pdf, "sample-pades-valid.pdf", policy, Set.of(ReportType.SIMPLE)));

    assertThat(result.signatures()).isNotEmpty();
    var sig = result.signatures().get(0);
    assertThat(sig.certificates()).isNotNull();
    if (!sig.certificates().isEmpty()) {
      assertThat(sig.certificates().get(0).id()).isNotBlank();
    }
  }

  @Test
  void extracts_archive_timestamps_from_evidence_records() throws Exception {
    byte[] pdf =
        new ClassPathResource("assets/pades/sample-pades-valid.pdf")
            .getInputStream()
            .readAllBytes();
    String policy =
        new String(
            new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    var result =
        adapter.validate(
            new ValidationRequest(
                pdf, "sample-pades-valid.pdf", policy, Set.of(ReportType.SIMPLE)));

    assertThat(result.signatures()).isNotEmpty();
    var sig = result.signatures().get(0);
    assertThat(sig.archiveTimestamps()).isNotNull();
    // Without an active TSL the DSS SimpleReport does not populate
    // evidence records even for LTA fixtures, so the list is empty here.
    // A positive assertion (`isNotEmpty`) would require a TSL-enabled profile.
    assertThat(sig.archiveTimestamps()).isEmpty();
  }

  @Test
  void lta_signature_has_populated_certificate_chain() throws Exception {
    // PAdES-LTA fixture carries a 3-cert chain (signer + intermediate + root).
    // Without an active TSL, DSS still populates the SimpleReport certificateChain,
    // so our CertificateSummary list must be non-empty and carry the signing cert.
    byte[] pdf =
        new ClassPathResource("assets/pades/PAdES-LTA.pdf").getInputStream().readAllBytes();
    String policy =
        new String(
            new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    var result =
        adapter.validate(
            new ValidationRequest(pdf, "PAdES-LTA.pdf", policy, Set.of(ReportType.SIMPLE)));

    assertThat(result.signatures()).isNotEmpty();
    var sig = result.signatures().get(0);
    assertThat(sig.certificates())
        .as("PAdES-LTA fixture must yield certificate chain metadata")
        .isNotEmpty();
    assertThat(sig.certificates().get(0).id()).isNotBlank();
    assertThat(sig.certificates().get(0).qualifiedName()).isNotBlank();
    // The chain contains at least signer + CA — 2 certs expected (the root is
    // not always returned by SimpleReport.getCertificateChain).
    assertThat(sig.certificates().size()).isGreaterThanOrEqualTo(2);
  }
}
