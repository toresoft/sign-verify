package org.toresoft.signverify.adapter.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.toresoft.signverify.domain.port.DocumentStoragePort;

/**
 * Unit tests for {@link FilesystemDocumentStorageAdapter}.
 *
 * <p>These tests exercise the adapter in isolation (no Spring context) by constructing it directly
 * with a {@link TempDir}-backed base directory.
 */
class FilesystemDocumentStorageAdapterTest {

  private final String jobId = "job-123";

  private DocumentStoragePort newAdapter(Path baseDir) throws IOException {
    return new FilesystemDocumentStorageAdapter(baseDir.toString());
  }

  @Test
  void storeInput_writesFileWithSafeName_andReturnsPath(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);
    byte[] content = "hello world".getBytes();

    String returnedPath = storage.storeInput(jobId, "doc.pdf", content);

    Path expected = baseDir.resolve(jobId).resolve("input-doc.pdf");
    assertThat(returnedPath).isEqualTo(expected.toString());
    assertThat(Files.exists(expected)).isTrue();
    assertThat(Files.readAllBytes(expected)).containsExactly(content);
  }

  @Test
  void storeInput_nullFilename_defaultsToFile(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);
    byte[] content = "payload".getBytes();

    String returnedPath = storage.storeInput(jobId, null, content);

    Path expected = baseDir.resolve(jobId).resolve("input-file");
    assertThat(returnedPath).isEqualTo(expected.toString());
    assertThat(Files.exists(expected)).isTrue();
    assertThat(Files.readAllBytes(expected)).containsExactly(content);
  }

  @Test
  void storeInput_specialChars_areSanitized(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);
    byte[] content = "x".getBytes();

    String returnedPath = storage.storeInput(jobId, "my file(1).pdf", content);

    // Spaces and parentheses must be replaced with '_' ; '.' is preserved.
    Path expected = baseDir.resolve(jobId).resolve("input-my_file_1_.pdf");
    assertThat(returnedPath).isEqualTo(expected.toString());
    assertThat(Files.exists(expected)).isTrue();
    assertThat(Files.readAllBytes(expected)).containsExactly(content);
  }

  @Test
  void storeResult_writesResultJson(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);
    byte[] content = "{\"ok\":true}".getBytes();

    String returnedPath = storage.storeResult(jobId, content);

    Path expected = baseDir.resolve(jobId).resolve("result.json");
    assertThat(returnedPath).isEqualTo(expected.toString());
    assertThat(Files.exists(expected)).isTrue();
    assertThat(Files.readAllBytes(expected)).containsExactly(content);
  }

  @Test
  void read_returnsWrittenBytes(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);
    byte[] written = "round-trip".getBytes();

    String stored = storage.storeInput(jobId, "data.bin", written);
    byte[] read = storage.read(stored);

    assertThat(read).containsExactly(written);
  }

  @Test
  void delete_removesFile(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);
    String stored = storage.storeInput(jobId, "to-delete.bin", "bye".getBytes());
    assertThat(Files.exists(Path.of(stored))).isTrue();

    storage.delete(stored);

    assertThat(Files.exists(Path.of(stored))).isFalse();
  }

  @Test
  void delete_nullPath_isNoOp(@TempDir Path baseDir) throws IOException {
    DocumentStoragePort storage = newAdapter(baseDir);

    assertThatCode(() -> storage.delete(null)).doesNotThrowAnyException();
  }

  @Test
  void constructor_createsBaseDirIfMissing(@TempDir Path parent) throws IOException {
    // Nested directory that does not yet exist on disk.
    Path missing = parent.resolve("nested").resolve("jobs");

    assertThat(Files.exists(missing)).isFalse();

    // Construction must not throw and must create the directory.
    DocumentStoragePort storage = newAdapter(missing);

    assertThat(Files.isDirectory(missing)).isTrue();

    // Sanity: the adapter is functional after the constructor created the base.
    byte[] payload = "ok".getBytes();
    String stored = storage.storeInput(jobId, "x.txt", payload);
    assertThat(Files.exists(Path.of(stored))).isTrue();
  }
}
