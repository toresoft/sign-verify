# Multipart Metadata Parts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Espone i parametri extra della verifica firma come part multipart dedicati (`profileId`, `profileOverrides`, `reports`, e per l'async `callbackUrl/Secret/Algorithm`), mantenendo il vecchio part `metadata` JSON come percorso legacy deprecato e mutuamente esclusivo.

**Architecture:** Un componente condiviso `VerificationRequestParts` (record + factory statica) risolve i valori grezzi da una delle due modalità e applica la regola di mutua esclusività (compresenza → 400). I due controller restano sottili: chiamano il resolver e poi applicano il loro mapping downstream **invariato** (sync strict sui report, async lenient). `POST /api/v1/extractions` non è toccato.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC multipart (`@RequestPart`), Jackson 3 (`tools.jackson.databind.json.JsonMapper`), JUnit 5 + AssertJ + Mockito, `@WebMvcTest` per gli slice controller, Maven.

## Global Constraints

- Java 21, Spring Boot 4.1.0, Maven (no wrapper). Se `mvn` non è sul `PATH`: `source ~/.sdkman/bin/sdkman-init.sh`.
- Jackson 3: usare `tools.jackson.databind.json.JsonMapper` e `tools.jackson.core.type.TypeReference`.
- API design-first: editare `src/main/resources/openapi/openapi.yaml`; lo spec deve restare OpenAPI valido (lo carica `OpenApiContractIT`).
- Errori custom via `AppException.badRequest(String)` → emessi come `application/problem+json` (RFC 9457).
- `mvn spotless:apply` (Google Java Format) obbligatorio prima di ogni commit.
- Commit git in inglese, **senza** footer `Co-Authored-By`.
- Type hints stretti; constructor injection; i controller non espongono entità.

---

## File Structure

- **Create** `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java` — resolver condiviso (record + factory + helper `parseOverrides`).
- **Create** `src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java` — unit del resolver.
- **Modify** `src/main/resources/openapi/openapi.yaml:263-271` (verifySync) e `:294-300` (verifyAsync) — nuovo schema multipart.
- **Modify** `src/main/java/org/toresoft/signverify/api/VerificationController.java` — nuovi `@RequestPart`, uso del resolver, rimozione di `Metadata`/`parseMetadata`.
- **Create** `src/test/java/org/toresoft/signverify/api/VerificationControllerTest.java` — slice test del controller sync (oggi mancante).
- **Modify** `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java` — nuovi `@RequestPart`, uso del resolver.
- **Modify** `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java` — casi nuovi part + legacy + mix→400.

---

## Task 1: `VerificationRequestParts` resolver

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java`
- Test: `src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java`

**Interfaces:**
- Produces:
  - `record VerificationRequestParts(String profileId, String profileOverridesJson, List<String> reports, String callbackUrl, String callbackSecret, String callbackAlgorithm)`
  - `static VerificationRequestParts from(JsonMapper om, String metadataJson, String profileId, String profileOverridesJson, String reportsCsv, String callbackUrl, String callbackSecret, String callbackAlgorithm)` — risolve la modalità; compresenza metadata+part → `AppException.badRequest`.
  - `static Map<String,Object> parseOverrides(JsonMapper om, String json)` — `null` se `json==null`, altrimenti mappa; JSON invalido → `AppException.badRequest`.
- `reports()` è `null` quando il parametro non è fornito (i controller applicano i loro default).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=VerificationRequestPartsTest`
Expected: FAIL con errore di compilazione "cannot find symbol: class VerificationRequestParts".

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=VerificationRequestPartsTest`
Expected: PASS (8 test verdi).

- [ ] **Step 5: Format + commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh
mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java \
        src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java
git commit -m "feat(api): add VerificationRequestParts resolver for multipart metadata"
```

---

## Task 2: OpenAPI contract — split metadata into dedicated parts

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml:263-271` (verifySync) and `:294-300` (verifyAsync)

**Interfaces:**
- Consumes: nothing (documentation/contract only).
- Produces: schema documentato per i nuovi part; `metadata` marcato `deprecated: true`.

- [ ] **Step 1: Replace the `verifySync` multipart schema**

In `src/main/resources/openapi/openapi.yaml`, sotto `operationId: verifySync`, sostituire il blocco `multipart/form-data` (attualmente `file` + `metadata`) con:

```yaml
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file: { type: string, format: binary }
                profileId:
                  type: string
                  format: uuid
                  description: Verification profile to apply.
                profileOverrides:
                  type: string
                  description: JSON object with per-request policy overrides.
                reports:
                  type: string
                  description: "CSV of report types: simple|detailed|diagnostic|etsi (default: simple,etsi)."
                metadata:
                  type: string
                  deprecated: true
                  description: >-
                    DEPRECATED. Legacy JSON envelope with profileId?, profileOverrides?,
                    reports[]?. Mutually exclusive with the individual parts above.
```

- [ ] **Step 2: Replace the `verifyAsync` multipart schema**

Sotto `operationId: verifyAsync`, sostituire il blocco `multipart/form-data` con:

```yaml
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file: { type: string, format: binary }
                profileId:
                  type: string
                  format: uuid
                  description: Verification profile to apply.
                profileOverrides:
                  type: string
                  description: JSON object with per-request policy overrides.
                reports:
                  type: string
                  description: "CSV of report types: simple|detailed|diagnostic|etsi (default: simple,etsi)."
                callbackUrl:
                  type: string
                  description: Optional signed callback delivery URL.
                callbackSecret:
                  type: string
                  description: Shared secret for the callback signature.
                callbackAlgorithm:
                  type: string
                  description: Callback signature algorithm (e.g. HmacSHA256).
                metadata:
                  type: string
                  deprecated: true
                  description: >-
                    DEPRECATED. Legacy JSON envelope with profileId?, profileOverrides?,
                    reports[]?, callbackUrl?, callbackSecret?, callbackAlgorithm?.
                    Mutually exclusive with the individual parts above.
```

- [ ] **Step 3: Verify spec still valid + generator runs**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q compile`
Expected: BUILD SUCCESS (l'openapi-generator gira in fase `generate-sources`; nessun nuovo DTO perché gli schemi multipart sono inline). Se fallisce, controllare l'indentazione YAML dei due blocchi.

- [ ] **Step 4: Run the contract IT**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=OpenApiContractIT`
Expected: PASS (lo spec resta OpenAPI valido; l'IT valida `/actuator/health` contro lo spec).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/openapi/openapi.yaml
git commit -m "feat(api): document dedicated verification metadata parts, deprecate metadata"
```

---

## Task 3: `VerificationController` — consume dedicated parts (sync)

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/api/VerificationController.java`
- Test: `src/test/java/org/toresoft/signverify/api/VerificationControllerTest.java` (create)

**Interfaces:**
- Consumes: `VerificationRequestParts.from(...)`, `VerificationRequestParts.parseOverrides(...)` (Task 1).
- Produces: endpoint `POST /api/v1/verifications` che accetta `file`, `profileId?`, `profileOverrides?`, `reports?`, `metadata?`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/toresoft/signverify/api/VerificationControllerTest.java`:

```java
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.VerificationService;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.ValidationResult;
import org.toresoft.signverify.security.Principal;

@WebMvcTest(controllers = VerificationController.class)
class VerificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private VerificationService service;
  @MockitoBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;
  @MockitoBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  private final Principal owner =
      new Principal(PrincipalType.API_KEY, "owner-key-1", Role.STANDARD, "owner");

  private static Authentication authFor(Principal p) {
    return new UsernamePasswordAuthenticationToken(
        p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
  }

  private static MockMultipartFile sampleFile() {
    return new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4".getBytes());
  }

  private void stubOkResponse() {
    var result = new ValidationResult("PAdES", "TOTAL_PASSED", null, 1, Map.of(), List.of(), List.of());
    when(service.verifySync(any(VerificationService.VerifyRequest.class)))
        .thenReturn(new VerificationService.VerifyResponse("Default", false, result));
  }

  @Test
  void verify_withDedicatedParts_passesParsedValuesToService() throws Exception {
    stubOkResponse();
    UUID profileId = UUID.randomUUID();

    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("profileId", "", "text/plain", profileId.toString().getBytes()))
                .file(new MockMultipartFile("profileOverrides", "", "application/json", "{\"x\":1}".getBytes()))
                .file(new MockMultipartFile("reports", "", "text/plain", "simple,etsi".getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isOk());

    ArgumentCaptor<VerificationService.VerifyRequest> captor =
        ArgumentCaptor.forClass(VerificationService.VerifyRequest.class);
    verify(service).verifySync(captor.capture());
    VerificationService.VerifyRequest req = captor.getValue();
    assertThat(req.profileId()).isEqualTo(profileId);
    assertThat(req.overrides()).containsEntry("x", 1);
    assertThat(req.reports()).containsExactlyInAnyOrder(ReportType.SIMPLE, ReportType.ETSI);
  }

  @Test
  void verify_withLegacyMetadata_stillWorks() throws Exception {
    stubOkResponse();
    UUID profileId = UUID.randomUUID();
    String metadata = "{\"profileId\":\"" + profileId + "\",\"reports\":[\"simple\"]}";

    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("metadata", "", "application/json", metadata.getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isOk());

    ArgumentCaptor<VerificationService.VerifyRequest> captor =
        ArgumentCaptor.forClass(VerificationService.VerifyRequest.class);
    verify(service).verifySync(captor.capture());
    assertThat(captor.getValue().reports()).containsExactly(ReportType.SIMPLE);
  }

  @Test
  void verify_withBothMetadataAndParts_returns400() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/verifications")
                .file(sampleFile())
                .file(new MockMultipartFile("reports", "", "text/plain", "simple".getBytes()))
                .file(new MockMultipartFile("metadata", "", "application/json", "{\"profileId\":\"x\"}".getBytes()))
                .with(authentication(authFor(owner))))
        .andExpect(status().isBadRequest());

    verify(service, never()).verifySync(any());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=VerificationControllerTest`
Expected: FAIL — con il controller attuale (`profileId`/`reports` non sono part riconosciuti) i valori non arrivano al service e `verify_withDedicatedParts...` fallisce sugli assert; `verify_withBothMetadataAndParts_returns400` non ottiene 400.

- [ ] **Step 3: Rewrite the controller method**

In `src/main/java/org/toresoft/signverify/api/VerificationController.java`:

Sostituire il metodo `verify(...)` e rimuovere il record `Metadata` e il metodo `parseMetadata(...)`. Mantenere `parseReports(...)` invariato. Il metodo diventa:

```java
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> verify(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "profileId", required = false) String profileId,
      @RequestPart(value = "profileOverrides", required = false) String profileOverrides,
      @RequestPart(value = "reports", required = false) String reports,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    var parts =
        VerificationRequestParts.from(
            om, metadataJson, profileId, profileOverrides, reports, null, null, null);

    UUID pid = parts.profileId() == null ? null : UUID.fromString(parts.profileId());
    Map<String, Object> overrides = VerificationRequestParts.parseOverrides(om, parts.profileOverridesJson());
    if (overrides == null) {
      overrides = Map.of();
    }
    Set<ReportType> reportTypes = parseReports(parts.reports());

    var req =
        new VerificationService.VerifyRequest(
            file.getBytes(), file.getOriginalFilename(), pid, overrides, reportTypes);
    var res = service.verifySync(req);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("verifiedAt", OffsetDateTime.now().toString());
    out.put("profileUsed", res.profileName());
    out.put("overridesApplied", res.overridesApplied());
    out.put("signatureFormat", res.result().signatureFormat());
    out.put("indication", res.result().indication());
    out.put("subIndication", res.result().subIndication());
    out.put("signatureCount", res.result().signatureCount());
    out.put("signatures", res.result().signatures());
    out.put("timestamps", res.result().timestamps());
    Map<String, Object> reportsOut = new LinkedHashMap<>();
    for (var e : res.result().reportsJson().entrySet()) {
      reportsOut.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
    }
    out.put("reports", reportsOut);
    return ResponseEntity.ok(out);
  }
```

Rimuovere l'import ora inutilizzato `import tools.jackson.core.type.TypeReference;` (usato solo dal vecchio `parseMetadata`). Mantenere gli altri import.

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=VerificationControllerTest`
Expected: PASS (3 test verdi).

- [ ] **Step 5: Format + commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh
mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/api/VerificationController.java \
        src/test/java/org/toresoft/signverify/api/VerificationControllerTest.java
git commit -m "feat(api): consume dedicated metadata parts in sync verification endpoint"
```

---

## Task 4: `AsyncVerificationController` — consume dedicated parts (async)

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`
- Test: `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java`

**Interfaces:**
- Consumes: `VerificationRequestParts.from(...)`, `VerificationRequestParts.parseOverrides(...)` (Task 1).
- Produces: endpoint `POST /api/v1/verifications/async` che accetta `file`, `profileId?`, `profileOverrides?`, `reports?`, `callbackUrl?`, `callbackSecret?`, `callbackAlgorithm?`, `metadata?`.

- [ ] **Step 1: Add the failing tests**

In `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java`, aggiungere questi tre test (l'esistente `postAsync_parsesMetadata` copre già il percorso legacy):

```java
  @Test
  void postAsync_parsesDedicatedParts() throws Exception {
    UUID jobId = UUID.randomUUID();
    UUID profileId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .file(new MockMultipartFile("profileId", "", "text/plain", profileId.toString().getBytes()))
                .file(new MockMultipartFile("profileOverrides", "", "application/json", "{\"x\":1}".getBytes()))
                .file(new MockMultipartFile("reports", "", "text/plain", "simple,etsi".getBytes()))
                .file(new MockMultipartFile("callbackUrl", "", "text/plain", "https://hook.example/submit".getBytes()))
                .file(new MockMultipartFile("callbackSecret", "", "text/plain", "shh".getBytes()))
                .file(new MockMultipartFile("callbackAlgorithm", "", "text/plain", "HmacSHA256".getBytes()))
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted());

    ArgumentCaptor<AsyncJobService.SubmitRequest> captor =
        ArgumentCaptor.forClass(AsyncJobService.SubmitRequest.class);
    verify(asyncService).submit(captor.capture(), eq(owner));
    AsyncJobService.SubmitRequest req = captor.getValue();
    assertThat(req.profileId()).isEqualTo(profileId);
    assertThat(req.reports()).containsExactlyInAnyOrder(ReportType.SIMPLE, ReportType.ETSI);
    assertThat(req.callbackUrl()).isEqualTo("https://hook.example/submit");
    assertThat(req.callbackSecret()).isEqualTo("shh");
    assertThat(req.callbackAlgorithm()).isEqualTo("HmacSHA256");
    assertThat(req.overridesJson()).contains("\"x\":1");
  }

  @Test
  void postAsync_bothMetadataAndParts_returns400() throws Exception {
    MockMultipartFile meta =
        new MockMultipartFile("metadata", "", "application/json", "{\"profileId\":\"x\"}".getBytes());

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .file(new MockMultipartFile("reports", "", "text/plain", "simple".getBytes()))
                .file(meta)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isBadRequest());

    verify(asyncService, never()).submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class));
  }

  @Test
  void postAsync_defaultsReports_withDedicatedPartsButNoReports() throws Exception {
    UUID jobId = UUID.randomUUID();
    when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
        .thenReturn(jobId);

    mockMvc
        .perform(
            multipart("/api/v1/verifications/async")
                .file(sampleFile())
                .file(new MockMultipartFile("callbackUrl", "", "text/plain", "https://hook".getBytes()))
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isAccepted());

    ArgumentCaptor<AsyncJobService.SubmitRequest> captor =
        ArgumentCaptor.forClass(AsyncJobService.SubmitRequest.class);
    verify(asyncService).submit(captor.capture(), eq(owner));
    assertThat(captor.getValue().reports())
        .containsExactlyInAnyOrder(ReportType.SIMPLE, ReportType.ETSI);
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=AsyncVerificationControllerTest`
Expected: FAIL sui nuovi test (i part dedicati non sono riconosciuti dal controller attuale; `...returns400` non ottiene 400).

- [ ] **Step 3: Rewrite the `submit` method**

In `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`, sostituire il metodo `submit(...)`:

```java
  @PostMapping(value = "/async", consumes = "multipart/form-data")
  public ResponseEntity<Map<String, Object>> submit(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "profileId", required = false) String profileId,
      @RequestPart(value = "profileOverrides", required = false) String profileOverrides,
      @RequestPart(value = "reports", required = false) String reports,
      @RequestPart(value = "callbackUrl", required = false) String callbackUrl,
      @RequestPart(value = "callbackSecret", required = false) String callbackSecret,
      @RequestPart(value = "callbackAlgorithm", required = false) String callbackAlgorithm,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    var parts =
        VerificationRequestParts.from(
            om,
            metadataJson,
            profileId,
            profileOverrides,
            reports,
            callbackUrl,
            callbackSecret,
            callbackAlgorithm);

    UUID pid = parts.profileId() == null ? null : UUID.fromString(parts.profileId());
    Map<String, Object> overrides = VerificationRequestParts.parseOverrides(om, parts.profileOverridesJson());
    String overridesJson = overrides == null ? null : om.writeValueAsString(overrides);

    List<String> reportList = parts.reports() == null ? List.of("simple", "etsi") : parts.reports();
    Set<ReportType> reportTypes = EnumSet.noneOf(ReportType.class);
    for (String r : reportList) {
      try {
        reportTypes.add(ReportType.valueOf(r.toUpperCase()));
      } catch (Exception ignored) {
      }
    }
    if (reportTypes.isEmpty()) {
      reportTypes.add(ReportType.SIMPLE);
    }

    Principal actor =
        (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    UUID jobId =
        asyncService.submit(
            new AsyncJobService.SubmitRequest(
                file.getBytes(),
                file.getOriginalFilename(),
                pid,
                overridesJson,
                reportTypes,
                parts.callbackUrl(),
                parts.callbackSecret(),
                parts.callbackAlgorithm()),
            actor);

    return ResponseEntity.status(202)
        .header("Location", "/api/v1/verifications/jobs/" + jobId)
        .body(Map.of("jobId", jobId, "status", "PENDING"));
  }
```

Rimuovere l'import ora inutilizzato `import tools.jackson.core.type.TypeReference;` (era usato solo dal vecchio parsing inline; `buildResponse` usa `om.readTree` e non ne ha bisogno). Il wildcard `import java.util.*;` copre già `List`, `Map`, `Set`, `EnumSet`, `UUID`.

- [ ] **Step 4: Run the full controller test class**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q test -Dtest=AsyncVerificationControllerTest`
Expected: PASS — i nuovi test verdi e i preesistenti (incluso `postAsync_parsesMetadata`, percorso legacy) ancora verdi.

- [ ] **Step 5: Format + commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh
mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java \
        src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java
git commit -m "feat(api): consume dedicated metadata parts in async verification endpoint"
```

---

## Task 5: Full verification gate

**Files:** none (build/verify only).

**Interfaces:**
- Consumes: tutti i cambiamenti precedenti.
- Produces: build verde end-to-end.

- [ ] **Step 1: Run the full verify**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn verify`
Expected: BUILD SUCCESS — Spotless check, ArchUnit (`ArchitectureTest`), unit + Failsafe IT (Testcontainers Postgres) e JaCoCo tutti verdi.

- [ ] **Step 2: If anything fails, fix and re-run**

Se Spotless check fallisce: `mvn spotless:apply`, poi ri-committare l'hotfix di formato con `git commit -am "style: apply google-java-format"`. Se un IT fallisce, indagare con `mvn -q test -Dtest=<ClassName>` prima di procedere.

- [ ] **Step 3: (Optional) tag if this is a release point**

Nessun tag richiesto dal piano; lasciare al workflow di rilascio.

---

## Self-Review

**Spec coverage:**
- Contratto (nuovi part + `metadata` deprecated, mutua esclusività) → Task 2 (spec) + Task 1 (regola nel resolver).
- `profileOverrides` resta JSON → gestito in `VerificationRequestParts` / `parseOverrides` (Task 1), consumato nei controller (Task 3/4).
- `reports` CSV → `parseCsv` (Task 1), default per-controller preservati (Task 3 sync strict, Task 4 async lenient).
- Callback come tre part piatti (solo async) → Task 4 + spec Task 2.
- Mutua esclusività, anche parziale, → 400: `from(...)` (Task 1), test controller mix→400 (Task 3/4).
- Testing (unit resolver, slice sync/async, legacy, mix, contract IT, `mvn verify`) → Task 1–5.
- Extractions fuori scope → nessuna modifica pianificata. ✔

**Placeholder scan:** nessun TODO/TBD; ogni step di codice mostra il codice completo. ✔

**Type consistency:** `VerificationRequestParts.from(JsonMapper, String metadataJson, String profileId, String profileOverrides, String reportsCsv, String callbackUrl, String callbackSecret, String callbackAlgorithm)` e `parseOverrides(JsonMapper, String)` usati con firme identiche in Task 1/3/4. `VerifyRequest(file, filename, UUID profileId, Map<String,Object> overrides, Set<ReportType> reports)` e `SubmitRequest(file, filename, UUID, String overridesJson, Set<ReportType>, String callbackUrl, String callbackSecret, String callbackAlgorithm)` combaciano con le definizioni esistenti. ✔
