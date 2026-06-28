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
}
