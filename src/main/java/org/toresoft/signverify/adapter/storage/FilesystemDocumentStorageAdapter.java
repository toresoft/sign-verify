package org.toresoft.signverify.adapter.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
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
public class FilesystemDocumentStorageAdapter implements DocumentStoragePort {

  private static final String INPUT_PREFIX = "input-";
  private static final String RESULT_NAME = "result.json";
  private static final String DEFAULT_INPUT_NAME = "file";
  private static final String SAFE_NAME_REGEX = "[^a-zA-Z0-9._-]";

  private final Path baseDir;

  public FilesystemDocumentStorageAdapter(@Value("${app.storage.jobs-dir}") String dir)
      throws IOException {
    this.baseDir = Path.of(dir);
    Files.createDirectories(baseDir);
  }

  @Override
  public String storeInput(String jobId, String filename, byte[] content) {
    String name = (filename == null) ? DEFAULT_INPUT_NAME : safeName(filename);
    return write(jobId, INPUT_PREFIX + name, content);
  }

  @Override
  public String storeResult(String jobId, byte[] content) {
    return write(jobId, RESULT_NAME, content);
  }

  @Override
  public byte[] read(String path) {
    try {
      return Files.readAllBytes(Path.of(path));
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
      Files.deleteIfExists(Path.of(path));
    } catch (IOException ignored) {
      // best-effort delete; nothing actionable for the caller
    }
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

  private static String safeName(String filename) {
    return filename.replaceAll(SAFE_NAME_REGEX, "_");
  }
}
