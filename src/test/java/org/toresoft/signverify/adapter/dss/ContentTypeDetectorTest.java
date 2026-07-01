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

import org.junit.jupiter.api.Test;
import org.toresoft.signverify.adapter.dss.ContentTypeDetector.DetectedType;

class ContentTypeDetectorTest {

  @Test
  void detects_pdf() {
    DetectedType t = ContentTypeDetector.detect("%PDF-1.7\n".getBytes());
    assertThat(t.mimeType()).isEqualTo("application/pdf");
    assertThat(t.extension()).isEqualTo(".pdf");
  }

  @Test
  void detects_zip() {
    byte[] zip = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
    assertThat(ContentTypeDetector.detect(zip).extension()).isEqualTo(".zip");
  }

  @Test
  void detects_xml_with_declaration() {
    assertThat(ContentTypeDetector.detect("<?xml version=\"1.0\"?>".getBytes()).extension())
        .isEqualTo(".xml");
  }

  @Test
  void detects_xml_after_leading_whitespace() {
    assertThat(ContentTypeDetector.detect("  \n<root/>".getBytes()).extension()).isEqualTo(".xml");
  }

  @Test
  void detects_der_sequence_as_p7m() {
    byte[] der = {0x30, (byte) 0x82, 0x01, 0x00};
    DetectedType t = ContentTypeDetector.detect(der);
    assertThat(t.mimeType()).isEqualTo("application/pkcs7-mime");
    assertThat(t.extension()).isEqualTo(".p7m");
  }

  @Test
  void unknown_binary_falls_back_to_octet_stream_bin() {
    byte[] junk = {0x01, 0x02, 0x03, 0x04};
    DetectedType t = ContentTypeDetector.detect(junk);
    assertThat(t.mimeType()).isEqualTo("application/octet-stream");
    assertThat(t.extension()).isEqualTo(".bin");
  }

  @Test
  void null_or_empty_falls_back_to_bin() {
    assertThat(ContentTypeDetector.detect(null).extension()).isEqualTo(".bin");
    assertThat(ContentTypeDetector.detect(new byte[0]).extension()).isEqualTo(".bin");
  }

  @Test
  void synthetic_name_appends_extension_to_stem() {
    assertThat(ContentTypeDetector.syntheticName("document", "%PDF".getBytes()))
        .isEqualTo("document.pdf");
  }
}
