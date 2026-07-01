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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.port.ExtractionPort;

@SpringBootTest
@ActiveProfiles("test")
class DssExtractionAdapterTest {

  @Autowired private ExtractionPort extractor;

  @Test
  void extract_returns_result_from_pades() throws IOException {
    byte[] pdf =
        Files.readAllBytes(Path.of("src/test/resources/assets/pades/sample-pades-valid.pdf"));
    var result = extractor.extract(pdf, "sample.pdf");

    assertThat(result.signatureFormat()).isNotNull();
    // PAdES may return 0 originals (signature is embedded in the PDF itself)
    // The important thing is that extraction doesn't throw
  }

  @Test
  void unsigned_pdf_throws_error() {
    byte[] unsignedPdf = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A};
    assertThatThrownBy(() -> extractor.extract(unsignedPdf, "unsigned.pdf"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void pades_reports_real_signature_format_not_unknown() throws IOException {
    byte[] pdf =
        Files.readAllBytes(Path.of("src/test/resources/assets/pades/sample-pades-valid.pdf"));
    var result = extractor.extract(pdf, "sample.pdf");
    assertThat(result.signatureFormat()).isEqualTo("PAdES");
  }

  @Test
  void extract_works_when_filename_is_null() throws IOException {
    byte[] pdf =
        Files.readAllBytes(Path.of("src/test/resources/assets/pades/sample-pades-valid.pdf"));
    // Passing null filename must not throw; DSS still detects the PAdES form from content.
    var result = extractor.extract(pdf, null);
    assertThat(result.signatureFormat()).isEqualTo("PAdES");
  }
}
