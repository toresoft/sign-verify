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
package org.toresoft.signverify.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.DocumentStoragePort;

/**
 * Filesystem-backed implementation of {@link DocumentStoragePort}.
 *
 * <p>Layout:
 *
 * <pre>
 *   {baseDir}/{jobId}/input-{safeName}
 *   {baseDir}/{jobId}/result.json
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "filesystem", matchIfMissing = true)
public class FilesystemDocumentStorageAdapter implements DocumentStoragePort {

  private final Path baseDir;

  public FilesystemDocumentStorageAdapter(@Value("${app.storage.jobs-dir}") String dir)
      throws IOException {
    this.baseDir = Path.of(dir).toAbsolutePath().normalize();
    Files.createDirectories(baseDir);
  }

  @Override
  public String storeInput(String jobId, String filename, byte[] content) {
    return write(jobId, StorageKeys.inputLeaf(filename), content);
  }

  @Override
  public String storeResult(String jobId, byte[] content) {
    return write(jobId, StorageKeys.RESULT_NAME, content);
  }

  @Override
  public byte[] read(String path) {
    try {
      return Files.readAllBytes(confined(path));
    } catch (IOException e) {
      throw new IllegalStateException("read fail: " + path, e);
    }
  }

  @Override
  public void delete(String path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(confined(path));
    } catch (IOException ignored) {
      // best-effort delete; nothing actionable for the caller
    }
  }

  /**
   * Defense in depth: paths are only ever produced by {@link #write} and stored in the DB, but
   * refuse anything that resolves outside {@code baseDir} in case a tampered value ever reaches
   * this adapter.
   */
  private Path confined(String path) {
    Path p = Path.of(path).toAbsolutePath().normalize();
    if (!p.startsWith(baseDir)) {
      throw new IllegalArgumentException("path outside storage dir: " + path);
    }
    return p;
  }

  private String write(String jobId, String name, byte[] content) {
    try {
      Path jobDir = baseDir.resolve(jobId);
      Files.createDirectories(jobDir);
      Path file = jobDir.resolve(name);
      Files.write(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return file.toString();
    } catch (IOException e) {
      throw new IllegalStateException("write fail", e);
    }
  }
}
