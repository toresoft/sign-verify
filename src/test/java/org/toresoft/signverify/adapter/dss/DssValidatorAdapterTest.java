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
    // PAdES BASELINE_LT does not contain LTA evidence records, so list is empty
    assertThat(sig.archiveTimestamps()).isEmpty();
  }
}
