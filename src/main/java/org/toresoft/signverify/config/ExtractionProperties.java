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
package org.toresoft.signverify.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.extraction")
public class ExtractionProperties {

  /** When null, resolves lazily to {@code ${java.io.tmpdir}/sign-verify-extract}. */
  private Path tempDir;

  private int maxLeaves = 1000;
  private DataSize maxTotalBytes = DataSize.ofMegabytes(500);
  private DataSize maxLeafBytes = DataSize.ofMegabytes(200);
  private Cleanup cleanup = new Cleanup();

  public Path getTempDir() {
    return tempDir != null
        ? tempDir
        : Path.of(System.getProperty("java.io.tmpdir"), "sign-verify-extract");
  }

  public void setTempDir(Path v) {
    this.tempDir = v;
  }

  public int getMaxLeaves() {
    return maxLeaves;
  }

  public void setMaxLeaves(int v) {
    this.maxLeaves = v;
  }

  public long getMaxTotalBytes() {
    return maxTotalBytes.toBytes();
  }

  public void setMaxTotalBytes(DataSize v) {
    this.maxTotalBytes = v;
  }

  public long getMaxLeafBytes() {
    return maxLeafBytes.toBytes();
  }

  public void setMaxLeafBytes(DataSize v) {
    this.maxLeafBytes = v;
  }

  public Cleanup getCleanup() {
    return cleanup;
  }

  public void setCleanup(Cleanup v) {
    this.cleanup = v;
  }

  public static class Cleanup {
    private String cron = "0 */15 * * * *";
    private Duration orphanTtl = Duration.ofHours(1);

    public String getCron() {
      return cron;
    }

    public void setCron(String v) {
      this.cron = v;
    }

    public Duration getOrphanTtl() {
      return orphanTtl;
    }

    public void setOrphanTtl(Duration v) {
      this.orphanTtl = v;
    }
  }
}
