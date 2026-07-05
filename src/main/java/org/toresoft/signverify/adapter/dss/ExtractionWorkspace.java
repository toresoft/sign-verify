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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.toresoft.signverify.config.ExtractionProperties;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractedFile;

/**
 * Per-request scratch area for recursive extraction. Extracted leaves are streamed to temp files
 * under a unique workdir instead of being retained in heap, bounding memory to O(1) copies. Leaf
 * count and byte volume are capped to defend against decompression-bomb style nesting; a breach
 * fails fast with HTTP 413 before the offending bytes fully land. {@code close()} removes the
 * workdir; callers own the lifecycle (the HTTP layer closes it after the response is streamed).
 */
final class ExtractionWorkspace implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ExtractionWorkspace.class);
  private static final int BUFFER_SIZE = 64 * 1024;

  private final Path workdir;
  private final int maxLeaves;
  private final long maxLeafBytes;
  private final long maxTotalBytes;

  private int leafCount;
  private long totalBytes;

  ExtractionWorkspace(ExtractionProperties props) {
    this.maxLeaves = props.getMaxLeaves();
    this.maxLeafBytes = props.getMaxLeafBytes();
    this.maxTotalBytes = props.getMaxTotalBytes();
    try {
      Path base = props.getTempDir();
      Files.createDirectories(base);
      this.workdir =
          Files.createDirectory(base.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID()));
    } catch (IOException e) {
      throw AppException.dssUnavailable("cannot create extraction workspace: " + e.getMessage());
    }
  }

  ExtractedFile spill(String filename, String mimeType, InputStream content) {
    if (leafCount >= maxLeaves) {
      throw AppException.tooLarge("extracted document count exceeds cap " + maxLeaves);
    }
    Path target = workdir.resolve("leaf-" + leafCount);
    long written = 0;
    try (InputStream in = content;
        OutputStream out = Files.newOutputStream(target)) {
      byte[] buf = new byte[BUFFER_SIZE];
      int n;
      while ((n = in.read(buf)) != -1) {
        written += n;
        if (written > maxLeafBytes) {
          throw AppException.tooLarge(
              "extracted document exceeds per-file cap " + maxLeafBytes + " bytes");
        }
        if (totalBytes + written > maxTotalBytes) {
          throw AppException.tooLarge(
              "cumulative extracted size exceeds cap " + maxTotalBytes + " bytes");
        }
        out.write(buf, 0, n);
      }
    } catch (AppException e) {
      deleteQuietly(target);
      throw e;
    } catch (IOException e) {
      deleteQuietly(target);
      throw AppException.badRequest("cannot write extracted document: " + e.getMessage());
    }
    totalBytes += written;
    leafCount++;
    return new ExtractedFile(filename, mimeType, target, written);
  }

  @Override
  public void close() {
    if (!Files.exists(workdir)) {
      return;
    }
    try (var paths = Files.walk(workdir)) {
      paths.sorted(Comparator.reverseOrder()).forEach(ExtractionWorkspace::deleteQuietly);
    } catch (IOException e) {
      log.warn("Failed to clean extraction workspace {}: {}", workdir, e.getMessage());
    }
  }

  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
      // best-effort; the scheduled sweeper reclaims leftovers.
    }
  }
}
