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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import org.toresoft.signverify.config.ExtractionProperties;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort.ExtractedFile;

class ExtractionWorkspaceTest {

  private static ExtractionProperties props(
      Path base, int leaves, DataSize leafBytes, DataSize totalBytes) {
    var p = new ExtractionProperties();
    p.setTempDir(base);
    p.setMaxLeaves(leaves);
    p.setMaxLeafBytes(leafBytes);
    p.setMaxTotalBytes(totalBytes);
    return p;
  }

  @Test
  void spill_writes_content_and_reports_size(@TempDir Path base) throws Exception {
    var p = props(base, 10, DataSize.ofKilobytes(10), DataSize.ofKilobytes(100));
    byte[] payload = "hello world".getBytes();
    try (var ws = new ExtractionWorkspace(p)) {
      ExtractedFile f = ws.spill("doc.txt", "text/plain", new ByteArrayInputStream(payload));
      assertThat(f.filename()).isEqualTo("doc.txt");
      assertThat(f.mimeType()).isEqualTo("text/plain");
      assertThat(f.size()).isEqualTo(payload.length);
      assertThat(Files.readAllBytes(f.path())).isEqualTo(payload);
      assertThat(f.path()).startsWith(base);
    }
  }

  @Test
  void close_deletes_the_workdir(@TempDir Path base) throws Exception {
    var p = props(base, 10, DataSize.ofKilobytes(10), DataSize.ofKilobytes(100));
    Path leaf;
    Path workdir;
    try (var ws = new ExtractionWorkspace(p)) {
      leaf = ws.spill("a.txt", "text/plain", new ByteArrayInputStream(new byte[] {1, 2, 3})).path();
      workdir = leaf.getParent();
      assertThat(Files.exists(leaf)).isTrue();
    }
    assertThat(Files.exists(workdir)).isFalse();
  }

  @Test
  void per_leaf_cap_breach_throws_413(@TempDir Path base) throws Exception {
    var p = props(base, 10, DataSize.ofBytes(4), DataSize.ofKilobytes(100));
    try (var ws = new ExtractionWorkspace(p)) {
      AppException ex =
          org.assertj.core.api.Assertions.catchThrowableOfType(
              () ->
                  ws.spill(
                      "big.bin",
                      "application/octet-stream",
                      new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5})),
              AppException.class);
      assertThat(ex).isNotNull();
      assertThat(ex.getStatus()).isEqualTo(413);
    }
  }

  @Test
  void cumulative_cap_breach_throws_413(@TempDir Path base) throws Exception {
    var p = props(base, 10, DataSize.ofKilobytes(10), DataSize.ofBytes(6));
    try (var ws = new ExtractionWorkspace(p)) {
      ws.spill("a", "application/octet-stream", new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
      assertThatThrownBy(
              () ->
                  ws.spill(
                      "b",
                      "application/octet-stream",
                      new ByteArrayInputStream(new byte[] {5, 6, 7})))
          .isInstanceOf(AppException.class);
    }
  }

  @Test
  void leaf_count_cap_breach_throws_413(@TempDir Path base) throws Exception {
    var p = props(base, 1, DataSize.ofKilobytes(10), DataSize.ofKilobytes(100));
    try (var ws = new ExtractionWorkspace(p)) {
      ws.spill("a", "text/plain", new ByteArrayInputStream(new byte[] {1}));
      assertThatThrownBy(
              () -> ws.spill("b", "text/plain", new ByteArrayInputStream(new byte[] {2})))
          .isInstanceOf(AppException.class);
    }
  }
}
