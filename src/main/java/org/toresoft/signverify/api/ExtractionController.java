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
package org.toresoft.signverify.api;

import java.io.OutputStream;
import java.nio.file.Files;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.toresoft.signverify.domain.port.ExtractionPort;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractedFile;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractionResult;

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
  public ResponseEntity<StreamingResponseBody> extract(@RequestPart("file") MultipartFile file)
      throws Exception {
    ExtractionResult result = extractor.extract(file.getBytes(), file.getOriginalFilename());
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.add("X-Signature-Format", result.signatureFormat());
      headers.add("X-Document-Count", String.valueOf(result.originals().size()));

      if (result.originals().size() == 1) {
        ExtractedFile f = result.originals().get(0);
        headers.setContentType(MediaType.parseMediaType(f.mimeType()));
        headers.setContentDispositionFormData("attachment", safeEntryName(f.filename(), 0));
        headers.setContentLength(f.size());
        StreamingResponseBody body =
            out -> {
              try {
                Files.copy(f.path(), out);
              } finally {
                result.close();
              }
            };
        return ResponseEntity.ok().headers(headers).body(body);
      }

      headers.setContentType(MediaType.parseMediaType("application/zip"));
      headers.setContentDispositionFormData("attachment", "originals.zip");
      StreamingResponseBody body = out -> streamZip(result, out);
      return ResponseEntity.ok().headers(headers).body(body);
    } catch (RuntimeException e) {
      result.close();
      throw e;
    }
  }

  private static void streamZip(ExtractionResult result, OutputStream out)
      throws java.io.IOException {
    try (ZipOutputStream zos = new ZipOutputStream(out)) {
      Set<String> usedNames = new HashSet<>();
      int index = 0;
      for (ExtractedFile f : result.originals()) {
        String entryName = uniqueName(safeEntryName(f.filename(), index), usedNames);
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(f.path(), zos);
        zos.closeEntry();
        index++;
      }
    } finally {
      result.close();
    }
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
