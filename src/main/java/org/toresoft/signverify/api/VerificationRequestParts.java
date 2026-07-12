/*
 * sign-verify Copyright (C) 2026 toresoft
 *
 * This file is part of the "sign-verify" project and is licensed under the
 * GNU Lesser General Public License v3 or later. See the project headers for details.
 */
package org.toresoft.signverify.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.toresoft.signverify.domain.exception.AppException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resolves the extra verification request parameters from a multipart request. Two encodings are
 * supported and are mutually exclusive: the legacy {@code metadata} JSON part, or the individual
 * first-level parts. Supplying both (even partially) is a client error.
 *
 * <p>{@link #reports()} is {@code null} when not supplied, so each controller keeps its own default
 * and mapping semantics.
 */
public record VerificationRequestParts(
    String profileId,
    String profileOverridesJson,
    List<String> reports,
    String callbackUrl,
    String callbackSecret,
    String callbackAlgorithm) {

  public static VerificationRequestParts from(
      JsonMapper om,
      String metadataJson,
      String profileId,
      String profileOverridesJson,
      String reportsCsv,
      String callbackUrl,
      String callbackSecret,
      String callbackAlgorithm) {

    boolean hasMetadata = present(metadataJson);
    boolean hasParts =
        present(profileId)
            || present(profileOverridesJson)
            || present(reportsCsv)
            || present(callbackUrl)
            || present(callbackSecret)
            || present(callbackAlgorithm);

    if (hasMetadata && hasParts) {
      throw AppException.badRequest(
          "provide either the 'metadata' part or the individual parts, not both");
    }
    if (hasMetadata) {
      return fromMetadata(om, metadataJson);
    }
    return new VerificationRequestParts(
        trimToNull(profileId),
        trimToNull(profileOverridesJson),
        parseCsv(reportsCsv),
        trimToNull(callbackUrl),
        trimToNull(callbackSecret),
        trimToNull(callbackAlgorithm));
  }

  /** Parses the (nullable) profileOverrides JSON into a map, or {@code null} when absent. */
  public static Map<String, Object> parseOverrides(JsonMapper om, String json) {
    if (json == null) {
      return null;
    }
    try {
      return om.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw AppException.badRequest("invalid profileOverrides json");
    }
  }

  private static VerificationRequestParts fromMetadata(JsonMapper om, String metadataJson) {
    Map<String, Object> meta;
    try {
      meta = om.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw AppException.badRequest("invalid metadata json");
    }

    String overridesJson;
    try {
      overridesJson =
          meta.get("profileOverrides") == null
              ? null
              : om.writeValueAsString(meta.get("profileOverrides"));
    } catch (Exception e) {
      throw AppException.badRequest("invalid profileOverrides json");
    }

    List<String> reports = null;
    Object rawReports = meta.get("reports");
    if (rawReports instanceof List<?> list) {
      reports = new ArrayList<>();
      for (Object o : list) {
        reports.add(String.valueOf(o));
      }
    }

    return new VerificationRequestParts(
        stringOrNull(meta.get("profileId")),
        overridesJson,
        reports,
        stringOrNull(meta.get("callbackUrl")),
        stringOrNull(meta.get("callbackSecret")),
        stringOrNull(meta.get("callbackAlgorithm")));
  }

  private static List<String> parseCsv(String csv) {
    if (!present(csv)) {
      return null;
    }
    List<String> out = new ArrayList<>();
    for (String s : csv.split(",")) {
      String t = s.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  private static boolean present(String s) {
    return s != null && !s.isBlank();
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String stringOrNull(Object o) {
    return o == null ? null : String.valueOf(o);
  }
}
