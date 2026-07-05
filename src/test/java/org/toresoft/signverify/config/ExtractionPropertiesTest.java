package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ExtractionPropertiesTest {

  @Test
  void defaults_resolve_temp_dir_under_java_io_tmpdir() {
    var props = new ExtractionProperties();
    assertThat(props.getTempDir())
        .isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "sign-verify-extract"));
    assertThat(props.getMaxLeaves()).isEqualTo(1000);
    assertThat(props.getMaxTotalBytes()).isEqualTo(DataSize.ofMegabytes(500).toBytes());
    assertThat(props.getMaxLeafBytes()).isEqualTo(DataSize.ofMegabytes(200).toBytes());
    assertThat(props.getCleanup().getOrphanTtl()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void explicit_temp_dir_is_used_verbatim() {
    var props = new ExtractionProperties();
    props.setTempDir(Path.of("/var/lib/sign-verify/extract-tmp"));
    assertThat(props.getTempDir()).isEqualTo(Path.of("/var/lib/sign-verify/extract-tmp"));
  }
}
