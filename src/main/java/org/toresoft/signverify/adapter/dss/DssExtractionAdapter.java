package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
  @CircuitBreaker(name = "dssValidator", fallbackMethod = "fallback")
  public ExtractionResult extract(byte[] bytes, String filename) {
    DSSDocument doc = new InMemoryDocument(bytes, filename);
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
    String firstSigId = signatures.get(0).getId();

    List<DSSDocument> originals;
    try {
      originals = validator.getOriginalDocuments(firstSigId);
    } catch (Exception e) {
      throw AppException.badRequest("cannot extract originals: " + e.getMessage());
    }

    List<ExtractedFile> out = new ArrayList<>();
    for (DSSDocument o : originals) {
      try {
        out.add(
            new ExtractedFile(
                o.getName(),
                o.getMimeType() == null
                    ? "application/octet-stream"
                    : o.getMimeType().getMimeTypeString(),
                o.openStream().readAllBytes()));
      } catch (Exception e) {
        throw new IllegalStateException("cannot read extracted document", e);
      }
    }
    String format = "UNKNOWN";
    return new ExtractionResult(format, out);
  }

  public ExtractionResult fallback(byte[] bytes, String filename, Throwable t) {
    throw AppException.dssUnavailable("dss circuit breaker open: " + t.getMessage());
  }
}
