package org.toresoft.signverify.api;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.toresoft.signverify.domain.port.ExtractionPort;

@RestController
@RequestMapping("/api/v1/extractions")
public class ExtractionController {

  /** Cap entry/attachment names well under the 255-char limit some ZIP extractors enforce. */
  private static final int MAX_ENTRY_NAME_LENGTH = 200;

  private final ExtractionPort extractor;

  public ExtractionController(ExtractionPort extractor) {
    this.extractor = extractor;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> extract(@RequestPart("file") MultipartFile file) throws Exception {
    var result = extractor.extract(file.getBytes(), file.getOriginalFilename());

    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Signature-Format", result.signatureFormat());
    headers.add("X-Document-Count", String.valueOf(result.originals().size()));

    if (result.originals().size() == 1) {
      var f = result.originals().get(0);
      headers.setContentType(MediaType.parseMediaType(f.mimeType()));
      headers.setContentDispositionFormData("attachment", safeEntryName(f.filename(), 0));
      return ResponseEntity.ok().headers(headers).body(f.content());
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      Set<String> usedNames = new HashSet<>();
      int index = 0;
      for (var f : result.originals()) {
        String entryName = uniqueName(safeEntryName(f.filename(), index), usedNames);
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(f.content());
        zos.closeEntry();
        index++;
      }
    }
    headers.setContentType(MediaType.parseMediaType("application/zip"));
    headers.setContentDispositionFormData("attachment", "originals.zip");
    return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
  }

  /**
   * Reduces an embedded filename (which comes from the signed document and is therefore untrusted)
   * to a safe ZIP entry name: strips any path component, drops control characters, and rejects path
   * traversal tokens. Falls back to a positional name when nothing usable remains.
   */
  private static String safeEntryName(String filename, int index) {
    String fallback = "document-" + index;
    if (filename == null || filename.isBlank()) {
      return fallback;
    }
    String base = filename.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    if (slash >= 0) {
      base = base.substring(slash + 1);
    }
    base = base.replaceAll("[\\x00-\\x1f]", "").trim();
    if (base.isEmpty() || base.equals(".") || base.equals("..")) {
      return fallback;
    }
    // Cap overly long embedded names (some extractors truncate/fail past 255 chars), keeping a
    // reasonable extension when present.
    if (base.length() > MAX_ENTRY_NAME_LENGTH) {
      int dot = base.lastIndexOf('.');
      String ext = (dot > 0 && base.length() - dot <= 16) ? base.substring(dot) : "";
      base = base.substring(0, MAX_ENTRY_NAME_LENGTH - ext.length()) + ext;
    }
    // Defense-in-depth: a leading '-' can be parsed as an option by CLI tools (unzip, tar) when the
    // archive is extracted from a shell. Prefix '_' so the name is never treated as a flag.
    if (base.startsWith("-")) {
      base = "_" + base;
    }
    return base;
  }

  /** Ensures entry-name uniqueness within the archive by appending a stable {@code -N} suffix. */
  private static String uniqueName(String name, Set<String> used) {
    if (used.add(name)) {
      return name;
    }
    String stem = name;
    String ext = "";
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      stem = name.substring(0, dot);
      ext = name.substring(dot);
    }
    int counter = 1;
    String candidate;
    do {
      candidate = stem + "-" + counter + ext;
      counter++;
    } while (!used.add(candidate));
    return candidate;
  }
}
