package org.toresoft.signverify.domain.port;

import java.util.List;

public interface ExtractionPort {
  ExtractionResult extract(byte[] signedDocument, String filename);

  record ExtractedFile(String filename, String mimeType, byte[] content) {}

  record ExtractionResult(String signatureFormat, List<ExtractedFile> originals) {}
}
