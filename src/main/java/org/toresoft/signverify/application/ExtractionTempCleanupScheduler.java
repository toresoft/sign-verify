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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.config.ExtractionProperties;
import org.toresoft.signverify.domain.model.PrincipalType;

/**
 * Reclaims extraction temp workdirs left behind by requests that never reached the response
 * stream's {@code finally} (killed worker, aborted stream). The temp area is node-local, so this
 * runs on EVERY instance WITHOUT a ShedLock — a shared lock would leave other nodes' orphans
 * uncollected.
 */
@Component
public class ExtractionTempCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(ExtractionTempCleanupScheduler.class);

  private final Path tempDir;
  private final Duration orphanTtl;
  private final AuditService audit;

  public ExtractionTempCleanupScheduler(ExtractionProperties props, AuditService audit) {
    this.tempDir = props.getTempDir();
    this.orphanTtl = props.getCleanup().getOrphanTtl();
    this.audit = audit;
  }

  @Scheduled(cron = "${app.extraction.cleanup.cron}")
  public void cleanup() {
    if (!Files.isDirectory(tempDir)) {
      return;
    }
    Instant cutoff = Instant.now().minus(orphanTtl);
    int deleted = 0;
    List<Path> workdirs;
    try (var entries = Files.list(tempDir)) {
      workdirs = entries.filter(Files::isDirectory).toList();
    } catch (IOException e) {
      log.warn("Extraction temp sweep could not list {}: {}", tempDir, e.getMessage());
      return;
    }
    for (Path dir : workdirs) {
      try {
        if (Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff)) {
          deleteTree(dir);
          deleted++;
        }
      } catch (IOException e) {
        log.warn("Extraction temp sweep failed for {}: {}", dir, e.getMessage());
      }
    }

    log.info("Extraction temp sweep complete (deleted={}, ttl={})", deleted, orphanTtl);
    Map<String, Object> details = new HashMap<>();
    details.put("deleted", deleted);
    audit.log(
        PrincipalType.SYSTEM,
        "scheduler-extraction-temp-cleanup",
        AuditActions.EXTRACTION_TEMP_CLEANUP,
        "extraction-temp",
        null,
        true,
        details);
  }

  private static void deleteTree(Path dir) throws IOException {
    try (var paths = Files.walk(dir)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
