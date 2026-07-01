package org.toresoft.signverify.adapter.dss;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.tsp.cms.CMSTimeStampedData;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort;

/**
 * Recursively unwraps signed containers down to their original non-signed content.
 *
 * <p>Handles RFC 5544 TimeStampedData (TSD) — which DSS 6.4 cannot parse via {@code
 * SignedDocumentValidator.fromDocument()} — by unwrapping it with Bouncy Castle before delegating,
 * then recurses so a TSD wrapping a p7m/CAdES (or any DSS-supported container) is peeled all the
 * way to the leaf. Non-TSD containers are peeled through {@link DssExtractionAdapter}.
 *
 * <p>Depth is bounded by {@link #MAX_DEPTH} to defend against pathological nesting (e.g. long PAdES
 * revision chains or hand-crafted inputs). A non-container reached at {@code depth == 0} is a user
 * error and propagates; at {@code depth > 0} it is a legitimate leaf and is returned raw with a
 * content-sniffed name/mime.
 */
@Component
@Primary
public class RecursiveExtractionAdapter implements ExtractionPort {

  static final String TSD_FORMAT = "RFC5544_TSD";
  private static final int MAX_DEPTH = 10;

  private final DssExtractionAdapter delegate;

  public RecursiveExtractionAdapter(DssExtractionAdapter delegate) {
    this.delegate = delegate;
  }

  @Override
  @CircuitBreaker(name = "dssExtraction", fallbackMethod = "dssExtractionFallback")
  public ExtractionResult extract(byte[] bytes, String filename) {
    Accumulator acc = new Accumulator();
    extractRecursive(bytes, filename, 0, acc);
    return new ExtractionResult(acc.outerFormat == null ? "UNKNOWN" : acc.outerFormat, acc.leaves);
  }

  public ExtractionResult dssExtractionFallback(byte[] bytes, String filename, Throwable t) {
    if (t instanceof AppException) throw (AppException) t;
    throw AppException.dssUnavailable("dss extraction circuit breaker open: " + t.getMessage());
  }

  private void extractRecursive(byte[] bytes, String name, int depth, Accumulator acc) {
    if (depth > MAX_DEPTH) {
      throw AppException.badRequest("extraction nesting exceeds max depth " + MAX_DEPTH);
    }

    byte[] inner = tryUnwrapTsd(bytes);
    if (inner != null) {
      if (depth == 0) {
        acc.outerFormat = TSD_FORMAT;
      }
      extractRecursive(inner, deriveInnerName(name, inner), depth + 1, acc);
      return;
    }

    ExtractionResult one;
    try {
      one = delegate.extract(bytes, name);
    } catch (AppException parseError) {
      if (depth == 0) {
        throw parseError; // top-level input is not a signed container: real error
      }
      // depth > 0: not a container -> raw leaf
      var dt = ContentTypeDetector.detect(bytes);
      String leafName =
          (name == null || name.isBlank())
              ? ContentTypeDetector.syntheticName("document", bytes)
              : name;
      acc.leaves.add(new ExtractedFile(leafName, dt.mimeType(), bytes));
      return;
    }

    if (depth == 0) {
      acc.outerFormat = one.signatureFormat();
    }
    for (ExtractedFile f : one.originals()) {
      extractRecursive(f.content(), f.filename(), depth + 1, acc);
    }
  }

  /** Returns the unwrapped inner content if {@code bytes} is a valid RFC 5544 TSD, else null. */
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

  private static String deriveInnerName(String outerName, byte[] innerBytes) {
    String ext = ContentTypeDetector.detect(innerBytes).extension();
    String base;
    if (outerName == null || outerName.isBlank()) {
      base = "document";
    } else if (outerName.toLowerCase().endsWith(".tsd")) {
      base = outerName.substring(0, outerName.length() - 4);
    } else {
      base = outerName;
    }
    return base + ext;
  }

  /** Collects leaves plus the outermost container format across the recursion. */
  private static final class Accumulator {
    private final List<ExtractedFile> leaves = new ArrayList<>();
    private String outerFormat;
  }
}
