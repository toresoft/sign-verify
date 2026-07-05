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
package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.tsp.cms.CMSTimeStampedData;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.config.ExtractionProperties;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort;

/**
 * Recursively unwraps signed containers down to their original non-signed content, spilling each
 * leaf to a temp file via {@link ExtractionWorkspace} so heap stays O(1) copies regardless of the
 * nesting fan-out.
 *
 * <p>Handles RFC 5544 TimeStampedData (TSD) — which DSS 6.4 cannot parse via {@code
 * SignedDocumentValidator.fromDocument()} — by unwrapping it with Bouncy Castle before delegating,
 * then recurses so a TSD wrapping a p7m/CAdES (or any DSS-supported container) is peeled all the
 * way to the leaf. Non-TSD containers are peeled through {@link DssExtractionAdapter}.
 *
 * <p>Depth is bounded by {@link #MAX_DEPTH} to defend against pathological nesting (e.g. long PAdES
 * revision chains or hand-crafted inputs). A non-container reached at {@code depth == 0} is a user
 * error and propagates; at {@code depth > 0} it is a legitimate leaf and is spilled raw with a
 * content-sniffed name/mime. Leaf count and byte volume are bounded by the workspace caps.
 */
@Component
@Primary
public class RecursiveExtractionAdapter implements ExtractionPort {

  static final String TSD_FORMAT = "RFC5544_TSD";
  private static final int MAX_DEPTH = 10;

  private final DssExtractionAdapter delegate;
  private final ExtractionProperties props;

  public RecursiveExtractionAdapter(DssExtractionAdapter delegate, ExtractionProperties props) {
    this.delegate = delegate;
    this.props = props;
  }

  @Override
  @CircuitBreaker(name = "dssExtraction", fallbackMethod = "dssExtractionFallback")
  public ExtractionResult extract(byte[] bytes, String filename) {
    ExtractionWorkspace ws = new ExtractionWorkspace(props);
    Accumulator acc = new Accumulator(ws);
    try {
      extractRecursive(new InMemoryDocument(bytes, safeName(filename, bytes)), 0, acc);
    } catch (RuntimeException e) {
      ws.close();
      throw e;
    }
    return new ExtractionResult(
        acc.outerFormat == null ? "UNKNOWN" : acc.outerFormat, acc.leaves, ws);
  }

  public ExtractionResult dssExtractionFallback(byte[] bytes, String filename, Throwable t) {
    if (t instanceof AppException) throw (AppException) t;
    throw AppException.dssUnavailable("dss extraction circuit breaker open: " + t.getMessage());
  }

  private void extractRecursive(DSSDocument node, int depth, Accumulator acc) {
    if (depth > MAX_DEPTH) {
      throw AppException.badRequest("extraction nesting exceeds max depth " + MAX_DEPTH);
    }

    byte[] nodeBytes = readNode(node);

    byte[] inner = tryUnwrapTsd(nodeBytes);
    if (inner != null) {
      if (depth == 0) {
        acc.outerFormat = TSD_FORMAT;
      }
      extractRecursive(
          new InMemoryDocument(inner, deriveInnerName(node.getName(), inner)), depth + 1, acc);
      return;
    }

    DssExtractionAdapter.DssParse parsed;
    try {
      parsed = delegate.parse(node);
    } catch (AppException parseError) {
      if (depth == 0) {
        throw parseError; // top-level input is not a signed container: real error
      }
      spillLeaf(node, nodeBytes, acc); // depth > 0: not a container -> raw leaf
      return;
    }

    if (depth == 0) {
      acc.outerFormat = parsed.format();
    }
    for (DSSDocument child : parsed.originals()) {
      extractRecursive(child, depth + 1, acc);
    }
  }

  private void spillLeaf(DSSDocument node, byte[] nodeBytes, Accumulator acc) {
    var dt = ContentTypeDetector.detect(nodeBytes);
    String leafName =
        (node.getName() == null || node.getName().isBlank())
            ? ContentTypeDetector.syntheticName("document", nodeBytes)
            : node.getName();
    acc.leaves.add(
        acc.ws.spill(leafName, dt.mimeType(), new java.io.ByteArrayInputStream(nodeBytes)));
  }

  private static byte[] readNode(DSSDocument node) {
    try (var in = node.openStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw AppException.badRequest("cannot read extracted document: " + e.getMessage());
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

  private static String safeName(String filename, byte[] bytes) {
    return (filename == null || filename.isBlank())
        ? ContentTypeDetector.syntheticName("document", bytes)
        : filename;
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

  /** Collects leaf handles plus the outermost container format across the recursion. */
  private static final class Accumulator {
    private final ExtractionWorkspace ws;
    private final List<ExtractionPort.ExtractedFile> leaves = new ArrayList<>();
    private String outerFormat;

    Accumulator(ExtractionWorkspace ws) {
      this.ws = ws;
    }
  }
}
