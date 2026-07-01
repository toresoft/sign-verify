package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort;

@Component
public class DssExtractionAdapter implements ExtractionPort {

  private final CertificateVerifier certificateVerifier;

  public DssExtractionAdapter(CertificateVerifier certificateVerifier) {
    this.certificateVerifier = certificateVerifier;
  }

  @Override
  public ExtractionResult extract(byte[] bytes, String filename) {
    String effectiveName =
        (filename == null || filename.isBlank())
            ? ContentTypeDetector.syntheticName("document", bytes)
            : filename;
    DSSDocument doc = new InMemoryDocument(bytes, effectiveName);
    SignedDocumentValidator validator;
    try {
      validator = SignedDocumentValidator.fromDocument(doc);
    } catch (Exception e) {
      throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
    validator.setCertificateVerifier(certificateVerifier);

    var signatures = validator.getSignatures();
    if (signatures.isEmpty()) {
      throw AppException.signatureParseError("no signatures found");
    }
    var firstSignature = signatures.get(0);
    String firstSigId = firstSignature.getId();

    List<DSSDocument> originals;
    try {
      originals = validator.getOriginalDocuments(firstSigId);
    } catch (Exception e) {
      throw AppException.badRequest("cannot extract originals: " + e.getMessage());
    }

    List<ExtractedFile> out = new ArrayList<>();
    for (DSSDocument o : originals) {
      try {
        byte[] content = o.openStream().readAllBytes();
        String name =
            (o.getName() == null || o.getName().isBlank())
                ? ContentTypeDetector.syntheticName("document", content)
                : o.getName();
        String mime =
            o.getMimeType() == null
                ? ContentTypeDetector.detect(content).mimeType()
                : o.getMimeType().getMimeTypeString();
        out.add(new ExtractedFile(name, mime, content));
      } catch (Exception e) {
        throw new IllegalStateException("cannot read extracted document", e);
      }
    }

    String format;
    try {
      format = firstSignature.getSignatureForm().name();
    } catch (Exception e) {
      format = "UNKNOWN";
    }
    return new ExtractionResult(format, out);
  }
}
