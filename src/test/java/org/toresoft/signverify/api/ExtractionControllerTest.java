package org.toresoft.signverify.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.toresoft.signverify.domain.port.ExtractionPort;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractedFile;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractionResult;

@ExtendWith(MockitoExtension.class)
class ExtractionControllerTest {

  @Mock private ExtractionPort extractor;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new ExtractionController(extractor)).build();
  }

  @Test
  void extract_singleOriginal_sanitizesContentDispositionFilename() throws Exception {
    when(extractor.extract(any(), anyString()))
        .thenReturn(
            new ExtractionResult(
                "PAdES",
                List.of(new ExtractedFile("../../etc/passwd", "text/plain", new byte[] {1}))));

    var part = new MockMultipartFile("file", "in.pdf", "application/pdf", new byte[] {9});

    String disposition =
        mvc.perform(multipart("/api/v1/extractions").file(part))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Content-Disposition");

    // The traversal path is reduced to its basename; no separators leak into the header.
    assertThat(disposition).contains("passwd");
    assertThat(disposition).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
  }

  @Test
  void extract_singleOriginal_truncatesOverlongFilename() throws Exception {
    String longName = "a".repeat(1000) + ".pdf";
    when(extractor.extract(any(), anyString()))
        .thenReturn(
            new ExtractionResult(
                "PAdES", List.of(new ExtractedFile(longName, "application/pdf", new byte[] {1}))));

    var part = new MockMultipartFile("file", "in.pdf", "application/pdf", new byte[] {9});

    String disposition =
        mvc.perform(multipart("/api/v1/extractions").file(part))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Content-Disposition");

    String filename = disposition.replaceAll(".*filename=\"?([^\";]+)\"?.*", "$1");
    assertThat(filename.length()).isLessThanOrEqualTo(200);
    assertThat(filename).endsWith(".pdf");
  }

  @Test
  void extract_multipleOriginals_sanitizesZipEntryNames() throws Exception {
    when(extractor.extract(any(), anyString()))
        .thenReturn(
            new ExtractionResult(
                "ASiC-E",
                List.of(
                    new ExtractedFile("../../etc/passwd", "text/plain", new byte[] {1}),
                    new ExtractedFile("/abs/secret.txt", "text/plain", new byte[] {2}),
                    new ExtractedFile("doc.pdf", "application/pdf", new byte[] {3}),
                    new ExtractedFile("doc.pdf", "application/pdf", new byte[] {4}),
                    new ExtractedFile("..", "text/plain", new byte[] {5}),
                    new ExtractedFile("--verbose", "text/plain", new byte[] {6}))));

    var part =
        new MockMultipartFile(
            "file", "container.asice", "application/vnd.etsi.asic-e+zip", new byte[] {9});

    byte[] zip =
        mvc.perform(multipart("/api/v1/extractions").file(part))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    List<String> names = zipEntryNames(zip);

    // No path separators or traversal tokens survive.
    assertThat(names).noneMatch(n -> n.contains("/") || n.contains("\\"));
    assertThat(names).noneMatch(n -> n.equals("..") || n.equals("."));
    // Basenames are preserved; the absolute path is reduced to its basename.
    assertThat(names).contains("passwd", "secret.txt");
    // Duplicate "doc.pdf" is disambiguated with a stable suffix preserving the extension.
    assertThat(names).contains("doc.pdf", "doc-1.pdf");
    // Unusable name ("..") falls back to a positional name.
    assertThat(names).anyMatch(n -> n.startsWith("document-"));
    // A leading '-' is neutralized so the name can't be read as a CLI flag on extraction.
    assertThat(names).noneMatch(n -> n.startsWith("-"));
    assertThat(names).contains("_--verbose");
    // All entry names are unique.
    assertThat(names).doesNotHaveDuplicates();
  }

  private static List<String> zipEntryNames(byte[] zip) throws Exception {
    List<String> names = new ArrayList<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry e;
      while ((e = zis.getNextEntry()) != null) {
        names.add(e.getName());
        zis.closeEntry();
      }
    }
    return names;
  }
}
