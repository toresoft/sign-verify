package org.toresoft.signverify.adapter.dss;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.bouncycastle.tsp.cms.CMSTimeStampedData;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort;

/**
 * Adds RFC 5544 TimeStampedData (TSD) support in front of {@link DssExtractionAdapter}.
 *
 * <p>DSS 6.4 has no factory for {@code id-aa-timeStampedData}: {@code
 * SignedDocumentValidator.fromDocument()} throws {@code IllegalInputException} for a {@code .tsd}.
 * This decorator tries Bouncy Castle TSD unwrap <em>before</em> falling through to the delegate, so
 * the circuit breaker on the delegate never sees TSD parse failures.
 *
 * <p>All other formats (PAdES, CAdES, XAdES, JAdES, ASiC) pass through to the delegate untouched.
 */
@Component
@Primary
public class TsdAwareExtractionAdapter implements ExtractionPort {

  static final String TSD_FORMAT = "RFC5544_TSD";

  private final DssExtractionAdapter delegate;

  public TsdAwareExtractionAdapter(DssExtractionAdapter delegate) {
    this.delegate = delegate;
  }

  @Override
  @CircuitBreaker(name = "dssExtraction", fallbackMethod = "dssExtractionFallback")
  public ExtractionResult extract(byte[] bytes, String filename) {
    byte[] inner = tryUnwrapTsd(bytes);
    if (inner != null) {
      return new ExtractionResult(
          TSD_FORMAT,
          List.of(new ExtractedFile(deriveInnerName(filename), "application/octet-stream", inner)));
    }
    return delegate.extract(bytes, filename);
  }

  public ExtractionResult dssExtractionFallback(byte[] bytes, String filename, Throwable t) {
    if (t instanceof AppException) throw (AppException) t;
    throw AppException.dssUnavailable("dss extraction circuit breaker open: " + t.getMessage());
  }

  /**
   * Returns the unwrapped inner content if {@code bytes} is a valid RFC 5544 TSD, or {@code null}
   * if it is not (including malformed CMS, wrong OID, or missing inner content).
   */
  private byte[] tryUnwrapTsd(byte[] bytes) {
    try {
      CMSTimeStampedData tsd = new CMSTimeStampedData(bytes);
      byte[] content = tsd.getContent();
      if (content != null && content.length > 0) {
        return content;
      }
    } catch (Exception notTsd) {
      // Not a TSD — fall through to delegate.
    }
    return null;
  }

  private static String deriveInnerName(String outerName) {
    if (outerName != null && outerName.toLowerCase().endsWith(".tsd")) {
      return outerName.substring(0, outerName.length() - 4);
    }
    return outerName != null ? outerName + ".inner" : "tsd-content";
  }
}
