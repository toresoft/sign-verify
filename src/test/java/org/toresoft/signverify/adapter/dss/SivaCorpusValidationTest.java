package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
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
 * Data-driven validation tests over the imported Estonian SiVa signed-document corpus ({@code
 * src/test/resources/assets/siva}, 180+ fixtures across PAdES/CAdES/XAdES/ASiC).
 *
 * <p>The {@code test} profile runs with the TSL pipeline skipped, so there are no trust anchors:
 * structurally valid signatures resolve to {@code INDETERMINATE} ({@code
 * NO_CERTIFICATE_CHAIN_FOUND}) rather than {@code TOTAL_PASSED}. These tests therefore assert only
 * the <b>trust-independent</b> guarantees the corpus can prove without an active Trusted List:
 * parser robustness, signature-format detection, cryptographic-tamper detection, and the no-trust
 * invariant. Asserting {@code TOTAL_PASSED} would require a TSL-enabled profile with the
 * Estonian/SK trust anchors.
 */
@SpringBootTest
@ActiveProfiles("test")
class SivaCorpusValidationTest {

  @Autowired private SignatureValidatorPort validator; // @Primary TsdAware over Dss

  private static final String POLICY = loadPolicy();

  static Stream<Path> sivaCorpus() throws Exception {
    Path root = Path.of(SivaCorpusValidationTest.class.getResource("/assets/siva").toURI());
    return Files.walk(root)
        .filter(Files::isRegularFile)
        .filter(p -> !p.getFileName().toString().endsWith(".md"))
        .sorted();
  }

  /**
   * Every fixture must be handled without an uncaught failure: the validator either returns a
   * result or rejects the input with a domain {@link AppException} — never an unhandled runtime
   * exception (NPE, parser crash, …). For parsed results we also assert the no-trust invariant and,
   * when a signature is present, that its format is detected in the family expected from the
   * container type.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sivaCorpus")
  void fixture_is_handled_robustly(Path file) throws Exception {
    Path root = Path.of(getClass().getResource("/assets/siva").toURI());
    String topDir = root.relativize(file).getName(0).toString();
    byte[] bytes = Files.readAllBytes(file);

    ValidationResult result;
    try {
      result =
          validator.validate(
              new ValidationRequest(
                  bytes, file.getFileName().toString(), POLICY, Set.of(ReportType.SIMPLE)));
    } catch (AppException e) {
      return; // controlled rejection of an unparseable/unsupported fixture is acceptable
    }

    assertThat(result).isNotNull();
    // No trust anchors in the test profile, so nothing can reach TOTAL_PASSED.
    assertThat(result.indication())
        .as("no fixture may pass without a Trusted List: %s", file.getFileName())
        .isNotEqualTo("TOTAL_PASSED");

    if (!result.signatures().isEmpty()) {
      String fmt = result.signatures().get(0).signatureFormat();
      if (fmt != null && !fmt.isBlank()) {
        assertThat(fmt)
            .as("signature format family for %s", file.getFileName())
            .containsAnyOf(expectedFamilies(topDir));
      }
    }
  }

  /**
   * Cryptographic tampering is caught even without trust anchors: a modified digest or a broken
   * signature value yields {@code TOTAL_FAILED}, independent of the certificate chain. These two
   * SiVa fixtures are the corpus's trust-independent negative anchors.
   */
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "pdf/baseline_profile_test_files/hellopades-lt1-lt2-wrongDigestValue.pdf, HASH_FAILURE",
    "pdf/signing_certifacte_test_files/missing_signing_certificate_attribute.pdf, SIG_CRYPTO_FAILURE"
  })
  void cryptographic_tampering_is_detected_without_trust(
      String relPath, String expectedSubIndication) throws Exception {
    byte[] bytes = new ClassPathResource("assets/siva/" + relPath).getInputStream().readAllBytes();

    var result =
        validator.validate(
            new ValidationRequest(bytes, fileName(relPath), POLICY, Set.of(ReportType.SIMPLE)));

    assertThat(result.indication()).isEqualTo("TOTAL_FAILED");
    assertThat(result.subIndication()).isEqualTo(expectedSubIndication);
  }

  private static String[] expectedFamilies(String topDir) {
    return switch (topDir) {
      case "pdf" -> new String[] {"PAdES", "PDF"};
      case "cades" -> new String[] {"CAdES"};
      case "xades" -> new String[] {"XAdES"};
      // ASiC-E / ASiC-S wrap either CAdES or XAdES signatures.
      case "asice", "asics" -> new String[] {"CAdES", "XAdES"};
      default -> new String[] {"CAdES", "XAdES", "PAdES", "PDF"};
    };
  }

  private static String fileName(String relPath) {
    int i = relPath.lastIndexOf('/');
    return i < 0 ? relPath : relPath.substring(i + 1);
  }

  private static String loadPolicy() {
    try {
      return new String(
          new ClassPathResource("policy/BASIC.xml").getInputStream().readAllBytes(),
          StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("cannot load BASIC policy", e);
    }
  }
}
