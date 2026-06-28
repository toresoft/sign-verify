package org.toresoft.signverify.domain.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationResultShapeTest {

  @Test
  void carries_structured_signatures_and_timestamps() {
    var ts = new TimestampSummary("T-1", "PASSED", null, null, "QTSA");
    var sig =
        new SignatureSummary(
            "S-1",
            "TOTAL_PASSED",
            null,
            "PAdES_BASELINE_LT",
            "QESIG",
            "CN=Foo",
            null,
            null,
            List.of(),
            List.of(),
            List.of(ts));
    var r =
        new ValidationResult(
            "PAdES_BASELINE_LT", "TOTAL_PASSED", null, 1, Map.of(), List.of(sig), List.of(ts));

    assertThat(r.signatures())
        .singleElement()
        .extracting(SignatureSummary::signatureLevel)
        .isEqualTo("QESIG");
    assertThat(r.timestamps())
        .singleElement()
        .extracting(TimestampSummary::qualification)
        .isEqualTo("QTSA");
  }

  @Test
  void signature_summary_exposes_claimed_signing_time() {
    var ts = new TimestampSummary("T-1", "PASSED", null, null, "QTSA");
    var sig =
        new SignatureSummary(
            "S-1",
            "TOTAL_PASSED",
            null,
            "PAdES_BASELINE_LT",
            "QESIG",
            "CN=Foo",
            null,
            java.time.OffsetDateTime.parse("2024-01-01T00:00:00Z"),
            List.of(),
            List.of(),
            List.of(ts));

    assertThat(sig.claimedSigningTime())
        .isEqualTo(java.time.OffsetDateTime.parse("2024-01-01T00:00:00Z"));
  }
}
