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
package org.toresoft.signverify.domain.port;

import java.nio.file.Path;
import java.util.List;

public interface ExtractionPort {
  ExtractionResult extract(byte[] signedDocument, String filename);

  /** A single extracted leaf, backed by a temp file rather than an in-heap byte array. */
  record ExtractedFile(String filename, String mimeType, Path path, long size) {}

  /**
   * Result of an extraction. Owns the scratch resources holding the leaf files; callers MUST {@code
   * close()} it once the leaves have been consumed (streamed to the client).
   */
  record ExtractionResult(
      String signatureFormat, List<ExtractedFile> originals, AutoCloseable cleanup)
      implements AutoCloseable {

    /** Convenience for tests / callers with nothing to clean up. */
    public ExtractionResult(String signatureFormat, List<ExtractedFile> originals) {
      this(signatureFormat, originals, () -> {});
    }

    @Override
    public void close() {
      try {
        cleanup.close();
      } catch (Exception e) {
        throw new IllegalStateException("failed to release extraction resources", e);
      }
    }
  }
}
