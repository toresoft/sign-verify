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
package org.toresoft.signverify.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.exception.AppException;
import tools.jackson.databind.json.JsonMapper;

class VerificationRequestPartsTest {

  private final JsonMapper om = JsonMapper.builder().build();

  @Test
  void newParts_resolved() {
    var p =
        VerificationRequestParts.from(
            om,
            null,
            "11111111-1111-1111-1111-111111111111",
            "{\"x\":1}",
            "simple,etsi",
            "https://hook",
            "HmacSHA256",
            null);
    assertThat(p.profileId()).isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThat(p.profileName()).isNull();
    assertThat(p.profileOverridesJson()).isEqualTo("{\"x\":1}");
    assertThat(p.reports()).containsExactly("simple", "etsi");
    assertThat(p.callbackUrl()).isEqualTo("https://hook");
    assertThat(p.callbackAlgorithm()).isEqualTo("HmacSHA256");
  }

  @Test
  void profileName_resolved() {
    var p = VerificationRequestParts.from(om, null, null, null, null, null, null, " AGID ");
    assertThat(p.profileName()).isEqualTo("AGID");
    assertThat(p.profileId()).isNull();
  }

  @Test
  void profileNameAndProfileId_together_throws400() {
    assertThatThrownBy(
            () ->
                VerificationRequestParts.from(
                    om,
                    null,
                    "11111111-1111-1111-1111-111111111111",
                    null,
                    null,
                    null,
                    null,
                    "AGID"))
        .isInstanceOf(AppException.class);
  }

  @Test
  void legacyMetadata_withCallbackSecret_isRejected400() {
    String meta =
        "{\"profileId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"reports\":[\"simple\",\"etsi\"],"
            + "\"callbackUrl\":\"https://hook\",\"callbackSecret\":\"shh\","
            + "\"callbackAlgorithm\":\"HmacSHA256\",\"profileOverrides\":{\"x\":1}}";
    assertThatThrownBy(
            () -> VerificationRequestParts.from(om, meta, null, null, null, null, null, null))
        .isInstanceOf(AppException.class);
  }

  @Test
  void legacyMetadata_resolved() {
    String meta =
        "{\"profileId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"reports\":[\"simple\",\"etsi\"],"
            + "\"callbackUrl\":\"https://hook\","
            + "\"callbackAlgorithm\":\"HmacSHA256\",\"profileOverrides\":{\"x\":1}}";
    var p = VerificationRequestParts.from(om, meta, null, null, null, null, null, null);
    assertThat(p.profileId()).isEqualTo("22222222-2222-2222-2222-222222222222");
    assertThat(p.profileName()).isNull();
    assertThat(p.reports()).containsExactly("simple", "etsi");
    assertThat(p.callbackUrl()).isEqualTo("https://hook");
    assertThat(p.profileOverridesJson()).contains("\"x\":1");
  }

  @Test
  void metadataAndProfileName_together_throws400() {
    assertThatThrownBy(
            () ->
                VerificationRequestParts.from(
                    om, "{\"profileId\":\"x\"}", null, null, null, null, null, "AGID"))
        .isInstanceOf(AppException.class);
  }

  @Test
  void bothModes_evenPartial_throws400() {
    assertThatThrownBy(
            () ->
                VerificationRequestParts.from(
                    om, "{\"profileId\":\"x\"}", null, null, "simple", null, null, null))
        .isInstanceOf(AppException.class);
  }

  @Test
  void neither_yieldsNullsAndNullReports() {
    var p = VerificationRequestParts.from(om, null, null, null, null, null, null, null);
    assertThat(p.profileId()).isNull();
    assertThat(p.profileName()).isNull();
    assertThat(p.reports()).isNull();
    assertThat(p.profileOverridesJson()).isNull();
  }

  @Test
  void reportsCsv_trimsAndDropsBlanks() {
    var p =
        VerificationRequestParts.from(om, null, null, null, " simple , , etsi ", null, null, null);
    assertThat(p.reports()).containsExactly("simple", "etsi");
  }

  @Test
  void invalidMetadataJson_throws400() {
    assertThatThrownBy(
            () -> VerificationRequestParts.from(om, "not-json", null, null, null, null, null, null))
        .isInstanceOf(AppException.class);
  }

  @Test
  void parseOverrides_invalid_throws400() {
    assertThatThrownBy(() -> VerificationRequestParts.parseOverrides(om, "not-json"))
        .isInstanceOf(AppException.class);
  }

  @Test
  void parseOverrides_null_returnsNull() {
    assertThat(VerificationRequestParts.parseOverrides(om, null)).isNull();
  }
}
