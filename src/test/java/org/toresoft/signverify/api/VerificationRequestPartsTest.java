/*
 * sign-verify Copyright (C) 2026 toresoft
 *
 * This file is part of the "sign-verify" project and is licensed under the
 * GNU Lesser General Public License v3 or later. See the project headers for details.
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
            "shh",
            "HmacSHA256");
    assertThat(p.profileId()).isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThat(p.profileOverridesJson()).isEqualTo("{\"x\":1}");
    assertThat(p.reports()).containsExactly("simple", "etsi");
    assertThat(p.callbackUrl()).isEqualTo("https://hook");
    assertThat(p.callbackSecret()).isEqualTo("shh");
    assertThat(p.callbackAlgorithm()).isEqualTo("HmacSHA256");
  }

  @Test
  void legacyMetadata_resolved() {
    String meta =
        "{\"profileId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"reports\":[\"simple\",\"etsi\"],"
            + "\"callbackUrl\":\"https://hook\",\"callbackSecret\":\"shh\","
            + "\"callbackAlgorithm\":\"HmacSHA256\",\"profileOverrides\":{\"x\":1}}";
    var p = VerificationRequestParts.from(om, meta, null, null, null, null, null, null);
    assertThat(p.profileId()).isEqualTo("22222222-2222-2222-2222-222222222222");
    assertThat(p.reports()).containsExactly("simple", "etsi");
    assertThat(p.callbackUrl()).isEqualTo("https://hook");
    assertThat(p.profileOverridesJson()).contains("\"x\":1");
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
