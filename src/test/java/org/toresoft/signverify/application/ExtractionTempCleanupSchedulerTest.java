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
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.toresoft.signverify.config.ExtractionProperties;

class ExtractionTempCleanupSchedulerTest {

  private ExtractionProperties props(Path base) {
    var p = new ExtractionProperties();
    p.setTempDir(base);
    p.getCleanup().setOrphanTtl(Duration.ofHours(1));
    return p;
  }

  @Test
  void deletes_stale_workdirs_keeps_fresh(@TempDir Path base) throws Exception {
    Path stale = Files.createDirectory(base.resolve("1000-old"));
    Files.writeString(stale.resolve("leaf-0"), "x");
    Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(3))));

    Path fresh = Files.createDirectory(base.resolve("2000-new"));
    Files.writeString(fresh.resolve("leaf-0"), "y");

    var scheduler = new ExtractionTempCleanupScheduler(props(base), mock(AuditService.class));
    scheduler.cleanup();

    assertThat(Files.exists(stale)).isFalse();
    assertThat(Files.exists(fresh)).isTrue();
  }

  @Test
  void missing_temp_dir_is_a_noop(@TempDir Path base) {
    var scheduler =
        new ExtractionTempCleanupScheduler(
            props(base.resolve("does-not-exist")), mock(AuditService.class));
    scheduler.cleanup(); // must not throw
  }
}
