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
