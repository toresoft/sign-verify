package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.toresoft.signverify.config.DssConfiguration;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;

/**
 * Opt-in coverage of the real online revocation/AIA path that the rest of the suite mocks away (see
 * {@link org.toresoft.signverify.config.OfflineDssTestConfig}). Builds the <b>production</b>
 * verifier and validates a real signed document, so DSS performs live AIA/OCSP/CRL HTTP fetches.
 *
 * <p>Tagged {@code network}: excluded from the default {@code mvn test} / {@code mvn verify} build
 * and run explicitly with {@code mvn verify -Dgroups=network}. We assert only that validation
 * completes against the live endpoints, not the trust outcome (no TSL is loaded here).
 */
@Tag("network")
class DssRevocationNetworkIT {

  @Test
  void validates_a_real_document_over_live_revocation_endpoints() throws Exception {
    CertificateVerifier verifier =
        new DssConfiguration().certificateVerifier(new TrustedListsCertificateSource());
    DssValidatorAdapter adapter = new DssValidatorAdapter(verifier, new ObjectMapper());

    byte[] pdf =
        new ClassPathResource("assets/siva/pdf/baseline_profile_test_files/hellopades-lt-b.pdf")
            .getInputStream()
            .readAllBytes();
    String policy =
        new String(
            new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);

    ValidationResult result =
        adapter.validate(
            new ValidationRequest(pdf, "hellopades-lt-b.pdf", policy, Set.of(ReportType.SIMPLE)));

    assertThat(result).isNotNull();
    assertThat(result.indication()).isNotBlank();
  }
}
