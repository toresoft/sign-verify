package org.toresoft.signverify.api;

import java.io.ByteArrayOutputStream;
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
      headers.setContentDispositionFormData("attachment", f.filename());
      return ResponseEntity.ok().headers(headers).body(f.content());
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (var f : result.originals()) {
        zos.putNextEntry(new ZipEntry(f.filename()));
        zos.write(f.content());
        zos.closeEntry();
      }
    }
    headers.setContentType(MediaType.parseMediaType("application/zip"));
    headers.setContentDispositionFormData("attachment", "originals.zip");
    return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
  }
}
