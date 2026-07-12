# Server-Generated Callback Secret — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace client-supplied `callbackSecret` on async job submission with a server-generated secret returned once in the 202 response body, eliminating the weak-secret risk.

**Architecture:** New `SecretGeneratorPort` in the domain with a `SecureRandom`-based adapter. `AsyncJobService` generates the secret when `callbackUrl` is present, encrypts it as before, and returns the plaintext via a new `SubmitResult`. `callbackSecret` is removed from request input on both the multipart-parts path and the legacy `metadata` path, and its presence is rejected with `400`. No DB migration (existing `callback_secret_cipher` column reused).

**Tech Stack:** Java 21+, Spring Boot (Web MVC), JUnit 5, Mockito, AssertJ, Flyway (no migration here), Jackson 3 (`tools.jackson.databind`).

**Spec:** `docs/2026-07-12-server-generated-callback-secret-design.md`

## Global Constraints

- License header: every new `.java` file MUST start with the same LGPL-3.0 header used by all other source files in `src/main/java/org/toresoft/signverify/...` (copy verbatim from any existing file; only the `package` line changes).
- Package conventions: ports in `org.toresoft.signverify.domain.port`, crypto adapters in `org.toresoft.signverify.adapter.crypto`.
- Build system is **Maven** (not Gradle). `pom.xml` at repo root. Unit tests via `maven-surefire-plugin` (default `test` phase), integration tests (`*IT`) via `maven-failsafe-plugin` (`verify` phase). Both exclude the JUnit `network` tag unless `-Dgroups=network` is set.
- **Maven is NOT on the default PATH** in non-interactive shells — it is managed by sdkman. Prefix EVERY Maven command with sourcing the init script: `source ~/.sdkman/bin/sdkman-init.sh && mvn ...`. If for any reason sdkman is unavailable, fall back to the absolute path `~/.sdkman/candidates/maven/current/bin/mvn`. Java 21 (Eclipse Temurin) is the toolchain.
- Run a single **unit** test class: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*RandomSecretGeneratorAdapterTest' test`. Surefire's `-Dtest` accepts the `*Suffix` glob.
- Run a single **integration** test class: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dit.test='*AuthenticationIT' verify -Dtest=none` (failsafe uses `-Dit.test`; `-Dtest=none` skips surefire).
- Full suite (unit + IT): `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp verify`. This is what CI runs.
- `spotless:check` enforces formatting and runs in CI before `verify`. After any code edit, run `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:check` before committing; if it fails, run `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:apply` to auto-format then re-check.
- Tests are JUnit 5 + Mockito (inline mock-maker wired as agent) + AssertJ. Follow existing imports in neighbouring test files.
- No new application config properties — the generator has no tunable parameters.
- `AppException.badRequest(String detail)` is the project's validation-error convention (see `org.toresoft.signverify.domain.exception.AppException`). It maps to HTTP 400 with `urn:signverify:error:invalid-request`.
- Jackson 3 package prefix is `tools.jackson.databind` (NOT `com.fasterxml`). Follow existing imports.
- Do not touch: `AesGcmSecretCipherAdapter`, `CallbackWorker`, `HmacCallbackDispatcherAdapter`, `HmacSigner`, `ApiKeyService`, security package, DB migrations.
- Current branch is `develop`. Create a feature branch before starting (see Task 0).

---

## File Structure

**New files (3):**
- `src/main/java/org/toresoft/signverify/domain/port/SecretGeneratorPort.java` — port interface, one method `String generateCallbackSecret()`.
- `src/main/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapter.java` — `SecureRandom` + Base64URL adapter, `@Component`.
- `src/test/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapterTest.java` — unit tests for the adapter.

**Modified files (8):**
- `src/main/java/org/toresoft/signverify/application/AsyncJobService.java` — new `SubmitResult`, inject port, drop input secret field, generate+return secret.
- `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java` — drop `@RequestPart("callbackSecret")`, reject part, propagate `callbackSecret` to 202 body.
- `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java` — drop `callbackSecret` field/param, reject key in `metadata`.
- `src/main/resources/openapi/openapi.yaml` — remove input `callbackSecret`, add output `callbackSecret`.
- `src/test/.../application/AsyncJobServiceTest.java` — rewrite callback section.
- `src/test/.../api/AsyncVerificationControllerTest.java` — rewrite callback assertions.
- `src/test/.../api/VerificationRequestPartsTest.java` — drop field, add metadata-rejection test.
- `src/test/.../application/PersistedJsonRereadGoldenTest.java` — drop `callbackSecret` assertion.
- `src/test/resources/fixtures/persisted-job-3.5.json` — drop `callbackSecret` key from the `metadata` block.

---

## Task 0: Branch + baseline

**Files:** none

- [ ] **Step 1: Create a feature branch off `develop`**

```bash
git checkout develop
git pull --ff-only
git checkout -b feat/server-generated-callback-secret
```

- [ ] **Step 2: Confirm baseline build is green**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp verify`
Expected: BUILD SUCCESSFUL (all tests pass). If it is red before we start, stop and surface it.

- [ ] **Step 3: Confirm working tree clean**

Run: `git status`
Expected: nothing to commit, clean working tree.

---

## Task 1: SecretGeneratorPort + adapter (TDD)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/SecretGeneratorPort.java`
- Create: `src/main/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapter.java`
- Test: `src/test/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapterTest.java`

**Interfaces:**
- Produces: `SecretGeneratorPort.generateCallbackSecret() -> String` (used by Task 3).
- Produces: `RandomSecretGeneratorAdapter` Spring `@Component` bean (auto-wired into `AsyncJobService` in Task 3).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapterTest.java`:

```java
package org.toresoft.signverify.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RandomSecretGeneratorAdapterTest {

  private final RandomSecretGeneratorAdapter gen = new RandomSecretGeneratorAdapter();

  @Test
  void generate_startsWithSvcbkPrefix() {
    assertThat(gen.generateCallbackSecret()).startsWith("svcbk_");
  }

  @Test
  void generate_decodesTo256Bits() {
    String s = gen.generateCallbackSecret();
    // Strip "svcbk_" then base64url-decode (no padding) -> must be exactly 32 bytes.
    String payload = s.substring("svcbk_".length());
    byte[] decoded = Base64.getUrlDecoder().decode(payload + paddingFor(payload));
    assertThat(decoded).hasSize(32);
  }

  @Test
  void generate_isBase64UrlWithoutPadding() {
    String s = gen.generateCallbackSecret();
    String payload = s.substring("svcbk_".length());
    assertThat(payload).doesNotContain("=");
    // base64url alphabet only (A-Z a-z 0-9 - _)
    assertThat(payload).matches("^[A-Za-z0-9_-]+$");
  }

  @RepeatedTest(50)
  void generate_producesUniqueValues() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100; i++) {
      assertThat(seen.add(gen.generateCallbackSecret()))
          .as("duplicate secret after %d generations", i)
          .isTrue();
    }
  }

  // Base64url without padding must be re-padded before the standard decoder accepts it.
  private static String paddingFor(String payload) {
    int mod = payload.length() % 4;
    return mod == 0 ? "" : "=".repeat(4 - mod);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*RandomSecretGeneratorAdapterTest' test`
Expected: COMPILE FAILURE — `RandomSecretGeneratorAdapter` does not exist.

- [ ] **Step 3: Create the port interface**

Create `src/main/java/org/toresoft/signverify/domain/port/SecretGeneratorPort.java`. Copy the LGPL header verbatim from `src/main/java/org/toresoft/signverify/domain/port/SecretCipherPort.java`, then:

```java
package org.toresoft.signverify.domain.port;

public interface SecretGeneratorPort {
  /**
   * Generates a fresh callback signing secret. Format is opaque to callers; clients use the value
   * as the HMAC shared secret for verifying outbound callbacks.
   *
   * @return a fresh, non-null secret string
   */
  String generateCallbackSecret();
}
```

- [ ] **Step 4: Create the adapter**

Create `src/main/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapter.java`. Copy the LGPL header verbatim from `src/main/java/org/toresoft/signverify/adapter/crypto/AesGcmSecretCipherAdapter.java`, then:

```java
package org.toresoft.signverify.adapter.crypto;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.SecretGeneratorPort;

/**
 * Generates callback secrets using a cryptographically strong {@link SecureRandom}. Each secret is
 * 256 bits of entropy, base64url-encoded (no padding), and prefixed with {@code svcbk_} so it is
 * distinguishable from other tokens in logs and redaction rules.
 */
@Component
public class RandomSecretGeneratorAdapter implements SecretGeneratorPort {

  private static final int ENTROPY_BYTES = 32; // 256-bit
  private static final String PREFIX = "svcbk_";

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generateCallbackSecret() {
    byte[] buf = new byte[ENTROPY_BYTES];
    random.nextBytes(buf);
    return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*RandomSecretGeneratorAdapterTest' test`
Expected: BUILD SUCCESSFUL, 4 tests pass (the `@RepeatedTest(50)` counts as one test method declaration run 50 times).

- [ ] **Step 6: Verify formatting (Spotless)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:check`
Expected: BUILD SUCCESSFUL. If it fails, run `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:apply` to auto-format, then re-run `spotless:check`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/toresoft/signverify/domain/port/SecretGeneratorPort.java \
        src/main/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapter.java \
        src/test/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapterTest.java
git commit -m "feat(security): add SecretGeneratorPort with SecureRandom adapter"
```

---

## Task 2: Rewrite AsyncJobService (TDD)

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/application/AsyncJobService.java`
- Test: `src/test/java/org/toresoft/signverify/application/AsyncJobServiceTest.java`

**Interfaces:**
- Consumes: `SecretGeneratorPort.generateCallbackSecret()` (from Task 1).
- Produces: `AsyncJobService.SubmitRequest` — record with `callbackSecret` field REMOVED (used by Task 3 & 4).
- Produces: `AsyncJobService.SubmitResult(UUID jobId, String callbackSecret)` — NEW return type of `submit(...)` (used by Task 3).
- Produces: `submit(SubmitRequest, Principal) -> SubmitResult` — new signature.

**Breaking signature change:** `submit(...)` returns `SubmitResult`, not `UUID`. Every caller must be updated (Task 3 covers the only caller, the controller).

- [ ] **Step 1: Update the test class — drop old callback-secret tests, add new ones**

Open `src/test/java/org/toresoft/signverify/application/AsyncJobServiceTest.java`.

1a. Add a `@Mock private SecretGeneratorPort secretGenerator;` field and pass it into the constructor in `setUp()`. The constructor in `setUp()` currently has 9 args; insert `secretGenerator` as the 5th argument (after `audit`, before `5`). Update the `new AsyncJobService(...)` call to 10 args.

1b. In `setUp()`, add a lenient stub:
```java
lenient().when(secretGenerator.generateCallbackSecret()).thenReturn("svcbk_test-secret-value");
```

1c. Replace the `baseRequest()` factory — remove the `callbackSecret` arg (was `"secret-123"` at position 7) and the `callbackAlgorithm` stays. New `SubmitRequest` has 7 components (file, filename, profileId, overridesJson, reports, callbackUrl, callbackAlgorithm):

```java
private AsyncJobService.SubmitRequest baseRequest() {
  return new AsyncJobService.SubmitRequest(
      new byte[] {1, 2, 3},
      "doc.pdf",
      UUID.randomUUID(),
      "{\"k\":\"v\"}",
      Set.of(ReportType.SIMPLE, ReportType.ETSI),
      "https://callback.example/hook",
      "HmacSHA256");
}
```

1d. In `submit_createsJobWithExpectedFields`, the saved-job assertions stay; no `callbackSecret` on the saved job to assert (it was never asserted there). Keep as-is.

1e. **Delete** these three tests entirely:
- `submit_encryptsCallbackSecret`
- `submit_callbackUrlWithoutSecret_isRejected`
- `submit_callbackUrlWithBlankSecret_isRejected`

1f. **Delete** `submit_rejectsCallbackAlgorithmOutsideAllowlist` body's reference to `callbackSecret` by removing that constructor arg. The new request construction:
```java
var req =
    new AsyncJobService.SubmitRequest(
        new byte[] {1}, "doc.pdf", null, null, Set.of(ReportType.SIMPLE),
        "https://callback.example/hook", "HmacMD5");
```

1g. Same for `submit_acceptsAllowedAlgorithm_caseInsensitively` and `submit_nullCallbackAlgorithm_defaultsToHmacSHA256` and `submit_noCallbackUrl_nullSecret_doesNotEncrypt` — remove the `callbackSecret` arg (and any `"secret-123"` literal) from each `new SubmitRequest(...)`.

1h. **Add** new tests:

```java
@Test
void submit_generatesAndEncryptsSecretWhenCallbackUrlPresent() {
  var req = baseRequest();
  when(secretGenerator.generateCallbackSecret()).thenReturn("svcbk_gen");
  when(cipher.encrypt("svcbk_gen")).thenReturn("enc-cipher");
  when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

  service.submit(req, actor);

  verify(secretGenerator, times(1)).generateCallbackSecret();
  verify(cipher, times(1)).encrypt("svcbk_gen");
  ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
  verify(repo).save(captor.capture());
  assertThat(captor.getValue().getCallbackSecretCipher()).isEqualTo("enc-cipher");
}

@Test
void submit_returnsGeneratedSecretInResult() {
  var req = baseRequest();
  when(secretGenerator.generateCallbackSecret()).thenReturn("svcbk_gen");
  when(cipher.encrypt("svcbk_gen")).thenReturn("enc");
  when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

  AsyncJobService.SubmitResult result = service.submit(req, actor);

  assertThat(result.jobId()).isNotNull();
  // The PLAINTEXT is returned, not the ciphertext.
  assertThat(result.callbackSecret()).isEqualTo("svcbk_gen");
}

@Test
void submit_omitsSecretWhenNoCallbackUrl() {
  var req =
      new AsyncJobService.SubmitRequest(
          new byte[] {1}, "doc.pdf", UUID.randomUUID(), null,
          Set.of(ReportType.SIMPLE), null, "HmacSHA256");
  when(storage.storeInput(anyString(), anyString(), any())).thenReturn("p");

  AsyncJobService.SubmitResult result = service.submit(req, actor);

  verify(secretGenerator, never()).generateCallbackSecret();
  verify(cipher, never()).encrypt(anyString());
  ArgumentCaptor<ValidationJob> captor = ArgumentCaptor.forClass(ValidationJob.class);
  verify(repo).save(captor.capture());
  assertThat(captor.getValue().getCallbackSecretCipher()).isNull();
  assertThat(result.callbackSecret()).isNull();
}
```

1i. `submit_returnsGeneratedSecretInResult` and the existing tests currently expect `UUID returned = service.submit(...)`; change them to `AsyncJobService.SubmitResult r = service.submit(...)` and use `r.jobId()` where they used `returned`. Apply to `submit_createsJobWithExpectedFields` and any other test that captured the `UUID` return.

- [ ] **Step 2: Run tests to verify they fail (compile errors expected)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*AsyncJobServiceTest' test`
Expected: COMPILE FAILURE — `SubmitRequest` still has 8 components; `submit` returns `UUID`.

- [ ] **Step 3: Modify AsyncJobService — drop input field, add SubmitResult, inject port**

Open `src/main/java/org/toresoft/signverify/application/AsyncJobService.java`.

3a. Add import: `import org.toresoft.signverify.domain.port.SecretGeneratorPort;`

3b. Change the `SubmitRequest` record — remove the `callbackSecret` component. New record:

```java
public record SubmitRequest(
    byte[] file,
    String filename,
    UUID profileId,
    String overridesJson,
    Set<ReportType> reports,
    String callbackUrl,
    String callbackAlgorithm) {}
```

3c. Add a new `SubmitResult` record (place it right after `SubmitRequest`):

```java
public record SubmitResult(UUID jobId, String callbackSecret) {}
```

3d. Add field + constructor param for the generator. Add `private final SecretGeneratorPort secretGenerator;` to the field list, and add `SecretGeneratorPort secretGenerator` as a constructor parameter (place it right after `SecretCipherPort cipher`).

3e. In `submit(...)`, change the return type from `UUID` to `SubmitResult`. Remove the block:

```java
if (req.callbackUrl() != null) {
  validateCallbackUrl(req.callbackUrl());
  if (req.callbackSecret() == null || req.callbackSecret().isBlank()) {
    throw AppException.badRequest("callbackSecret is required when callbackUrl is provided");
  }
}
```

…and replace with:

```java
String callbackSecret = null;
if (req.callbackUrl() != null) {
  validateCallbackUrl(req.callbackUrl());
  callbackSecret = secretGenerator.generateCallbackSecret();
}
```

3f. Replace the secret-encryption block:

```java
if (req.callbackSecret() != null) {
  j.setCallbackSecretCipher(cipher.encrypt(req.callbackSecret()));
}
```

…with:

```java
if (callbackSecret != null) {
  j.setCallbackSecretCipher(cipher.encrypt(callbackSecret));
}
```

3g. Change the final `return jobId;` to:

```java
return new SubmitResult(jobId, callbackSecret);
```

3h. Update the Javadoc on `submit` to reflect the new return type (change `@return the generated job id` to `@return the generated job id and the plaintext callback secret (null when no callbackUrl)`).

- [ ] **Step 4: Run tests to verify they pass**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*AsyncJobServiceTest' test`
Expected: BUILD SUCCESSFUL, all AsyncJobServiceTest tests pass.

- [ ] **Step 5: Verify formatting (Spotless)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:check`
Expected: BUILD SUCCESSFUL. If it fails, run `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:apply` to auto-format, then re-run `spotless:check`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/toresoft/signverify/application/AsyncJobService.java \
        src/test/java/org/toresoft/signverify/application/AsyncJobServiceTest.java
git commit -m "feat(async): generate callback secret server-side, return it in SubmitResult"
```

---

## Task 3: Rewrite controller + VerificationRequestParts (TDD)

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`
- Modify: `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java`
- Test: `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java`
- Test: `src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java`

**Interfaces:**
- Consumes: `AsyncJobService.SubmitResult` (from Task 2).
- Consumes: `VerificationRequestParts` without `callbackSecret` field.
- Produces: 202 body now includes `callbackSecret` when a callback URL was supplied.

- [ ] **Step 1: Update VerificationRequestPartsTest first (drives the parts change)**

Open `src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java`.

1a. The existing test at ~line 40 calls `VerificationRequestParts.from(om, null, "1111...", null, "simple,etsi", "https://hook", "shh", "HmacSHA256", null)` — 9 args. Remove the `"shh"` (the `callbackSecret`) arg so the call has 8 args. Drop the `assertThat(p.callbackSecret()).isEqualTo("shh");` assertion.

1b. `legacyMetadata_resolved` builds a `meta` JSON containing `"callbackSecret":"shh"`. That must now be REJECTED. Change this test to assert a 400:

```java
@Test
void legacyMetadata_withCallbackSecret_isRejected400() {
  String meta =
      "{\"profileId\":\"22222222-2222-2222-2222-222222222222\","
          + "\"reports\":[\"simple\",\"etsi\"],"
          + "\"callbackUrl\":\"https://hook\",\"callbackSecret\":\"shh\","
          + "\"callbackAlgorithm\":\"HmacSHA256\",\"profileOverrides\":{\"x\":1}}";
  assertThatThrownBy(() -> VerificationRequestParts.from(om, meta, null, null, null, null, null, null))
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
```

1c. Every other call to `VerificationRequestParts.from(...)` in this file has 9 args; drop the `callbackSecret` arg (the 7th positional, right after the callbackUrl arg) from each so they become 8-arg calls. Check `profileName_resolved`, `profileNameAndProfileId_together_throws400`, `metadataAndProfileName_together_throws400`, and any others — grep for `.from(om` to find them all.

- [ ] **Step 2: Run VerificationRequestPartsTest to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*VerificationRequestPartsTest' test`
Expected: COMPILE FAILURE — `from(...)` still has 9 params; `callbackSecret()` accessor missing.

- [ ] **Step 3: Modify VerificationRequestParts — drop field, reject in metadata**

Open `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java`.

3a. In the record declaration, remove the `String callbackSecret,` component.

3b. In `public static VerificationRequestParts from(...)`, remove the `String callbackSecret,` parameter (keep the order otherwise identical). Remove `|| present(callbackSecret)` from the `hasParts` computation. Remove `trimToNull(callbackSecret),` from the final constructor call.

3c. In `fromMetadata`, after `meta` is parsed, add a rejection before reading any keys:

```java
if (meta.containsKey("callbackSecret")) {
  throw AppException.badRequest("callbackSecret is no longer accepted; it is generated by the server");
}
```

3d. In the return of `fromMetadata`, remove `stringOrNull(meta.get("callbackSecret")),` (the `callbackSecret` slot no longer exists in the record).

- [ ] **Step 4: Run VerificationRequestPartsTest to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*VerificationRequestPartsTest' test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Update controller tests (drives the controller change)**

Open `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java`.

5a. `postAsync_returns202WithJobId` and `postAsync_setsLocationHeader` stub `asyncService.submit(...)` to `thenReturn(jobId)`. Change both to return a `SubmitResult`:

```java
when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
    .thenReturn(new AsyncJobService.SubmitResult(jobId, null));
```

5b. Add a new test asserting the secret surfaces in the body when the service returns one:

```java
@Test
void postAsync_returnsCallbackSecretInBody_whenProvidedByService() throws Exception {
  UUID jobId = UUID.randomUUID();
  when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
      .thenReturn(new AsyncJobService.SubmitResult(jobId, "svcbk_xyz"));

  mockMvc
      .perform(
          multipart("/api/v1/verifications/async")
              .file(sampleFile())
              .with(authentication(authFor(owner, Role.STANDARD))))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.jobId").value(jobId.toString()))
      .andExpect(jsonPath("$.callbackSecret").value("svcbk_xyz"));
}

@Test
void postAsync_omitsCallbackSecretInBody_whenNull() throws Exception {
  UUID jobId = UUID.randomUUID();
  when(asyncService.submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class)))
      .thenReturn(new AsyncJobService.SubmitResult(jobId, null));

  mockMvc
      .perform(
          multipart("/api/v1/verifications/async")
              .file(sampleFile())
              .with(authentication(authFor(owner, Role.STANDARD))))
      .andExpect(status().isAccepted())
      .andExpect(jsonPath("$.callbackSecret").doesNotExist());
}
```

5c. Add tests asserting that a `callbackSecret` part / metadata key is rejected:

```java
@Test
void postAsync_callbackSecretPart_isRejected400() throws Exception {
  mockMvc
      .perform(
          multipart("/api/v1/verifications/async")
              .file(sampleFile())
              .file(new MockMultipartFile("callbackSecret", "", "text/plain", "shh".getBytes()))
              .with(authentication(authFor(owner, Role.STANDARD))))
      .andExpect(status().isBadRequest());

  verify(asyncService, never())
      .submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class));
}

@Test
void postAsync_callbackSecretInMetadata_isRejected400() throws Exception {
  String metadata =
      "{\"callbackUrl\":\"https://hook\",\"callbackSecret\":\"shh\","
          + "\"callbackAlgorithm\":\"HmacSHA256\"}";
  MockMultipartFile meta =
      new MockMultipartFile("metadata", "", "application/json", metadata.getBytes());

  mockMvc
      .perform(
          multipart("/api/v1/verifications/async")
              .file(sampleFile())
              .file(meta)
              .with(authentication(authFor(owner, Role.STANDARD))))
      .andExpect(status().isBadRequest());

  verify(asyncService, never())
      .submit(any(AsyncJobService.SubmitRequest.class), any(Principal.class));
}
```

5d. **Delete or update** the now-invalid tests:
- `postAsync_parsesMetadata`: its `metadata` JSON contains `"callbackSecret":"shh"` and the test asserts `req.callbackSecret()`. Remove the `"callbackSecret":"shh",` segment from the JSON string, and delete the `assertThat(req.callbackSecret()).isEqualTo("shh");` line.
- `postAsync_parsesDedicatedParts`: it sends a `callbackSecret` part and asserts `req.callbackSecret()`. This test must now become the rejection test — but since we already added `postAsync_callbackSecretPart_isRejected400` in 5c, **delete** `postAsync_parsesDedicatedParts` outright (its positive assertions are obsolete). Also delete its `callbackSecret` part line.

5e. Any other `thenReturn(jobId)` on `asyncService.submit` in this file must become `thenReturn(new AsyncJobService.SubmitResult(jobId, null))`. Grep for `asyncService.submit` and fix all stubs.

- [ ] **Step 6: Run controller tests to verify they fail**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*AsyncVerificationControllerTest' test`
Expected: COMPILE FAILURE — `submit` returns `UUID` in the controller (not yet updated), `SubmitResult` symbol resolution, etc.

- [ ] **Step 7: Modify AsyncVerificationController**

Open `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`.

7a. Remove the `@RequestPart(value = "callbackSecret", required = false) String callbackSecret,` parameter from `submit(...)`.

7b. Change the `VerificationRequestParts.from(om, ...)` call: drop the `callbackSecret` argument so it matches the new 8-arg signature.

7c. Drop the `parts.callbackSecret()` argument from the `new AsyncJobService.SubmitRequest(...)` call (now 7 components).

7d. Change `UUID jobId = asyncService.submit(...)` to `AsyncJobService.SubmitResult result = asyncService.submit(...)` and introduce `UUID jobId = result.jobId();`.

7e. After the parts are resolved and BEFORE calling `asyncService.submit(...)`, add the multipart-part rejection. The cleanest hook is the `multipart` request: inject `MultipartHttpServletRequest` is overkill; instead, use the existing `MultipartFile` parts. Since the controller already receives individual `@RequestPart` values, add a `HttpServletRequest` to read the raw multipart map. Minimal change — add this parameter to `submit(...)`:

```java
@RequestParam(value = "callbackSecret", required = false) MultipartFile rejectedCallbackSecret,
```

Wait — binding a removed part via `@RequestParam` will itself return null when absent (no 400). To ENFORCE 400 when present, use `MultipartHttpServletRequest`. Add parameter:

```java
jakarta.servlet.http.HttpServletRequest request,
```

Then, immediately after method entry (before any other logic):

```java
if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipart
    && multipart.getMultiFileMap().containsKey("callbackSecret")) {
  throw AppException.badRequest(
      "callbackSecret is no longer accepted; it is generated by the server");
}
```

(Place this as the very first statement in `submit(...)`.)

7f. Build the 202 body to include the secret only when non-null:

```java
java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
body.put("jobId", jobId);
body.put("status", "PENDING");
if (result.callbackSecret() != null) {
  body.put("callbackSecret", result.callbackSecret());
}
return ResponseEntity.status(202)
    .header("Location", "/api/v1/verifications/jobs/" + jobId)
    .body(body);
```

(Replace the existing `Map.of("jobId", jobId, "status", "PENDING")` — `Map.of` would serialize a null; the `LinkedHashMap` with the conditional add omits the key entirely.)

- [ ] **Step 8: Run controller tests to verify they pass**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*AsyncVerificationControllerTest' test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Verify formatting (Spotless)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:check`
Expected: BUILD SUCCESSFUL. If it fails, run `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:apply` to auto-format, then re-run `spotless:check`.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java \
        src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java \
        src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java \
        src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java
git commit -m "feat(api): remove callbackSecret from input, return it once in 202 body"
```

---

## Task 4: Collateral test fixes (golden + fixture)

**Files:**
- Modify: `src/test/java/org/toresoft/signverify/application/PersistedJsonRereadGoldenTest.java`
- Modify: `src/test/resources/fixtures/persisted-job-3.5.json`

- [ ] **Step 1: Read the fixture to confirm the key location**

Run: `grep -n callbackSecret src/test/resources/fixtures/persisted-job-3.5.json`
Expected: one line like `    "callbackSecret": "super-secret",` inside the `metadata` object.

- [ ] **Step 2: Remove the key from the fixture**

Edit `src/test/resources/fixtures/persisted-job-3.5.json`: delete the line `    "callbackSecret": "super-secret",` (mind the trailing comma on the preceding line — ensure JSON stays valid).

- [ ] **Step 3: Remove the assertion from the golden test**

Edit `src/test/java/org/toresoft/signverify/application/PersistedJsonRereadGoldenTest.java`: delete the line `assertThat(readBack.get("callbackSecret")).isEqualTo("super-secret");`.

- [ ] **Step 4: Run the golden test**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp -Dtest='*PersistedJsonRereadGoldenTest' test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify formatting (Spotless)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:check`
Expected: BUILD SUCCESSFUL. If it fails, run `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp spotless:apply` to auto-format, then re-run `spotless:check`.

- [ ] **Step 6: Commit**

```bash
git add src/test/resources/fixtures/persisted-job-3.5.json \
        src/test/java/org/toresoft/signverify/application/PersistedJsonRereadGoldenTest.java
git commit -m "test: drop callbackSecret from persisted-job golden fixture"
```

---

## Task 5: OpenAPI spec update

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`

- [ ] **Step 1: Remove callbackSecret from the multipart schema**

In `src/main/resources/openapi/openapi.yaml`, under `/api/v1/verifications/async` → `requestBody` → `multipart/form-data` → `schema` → `properties`, delete the `callbackSecret` property block (currently lines ~336-338):

```yaml
                callbackSecret:
                  type: string
                  description: Shared secret for the callback signature.
```

- [ ] **Step 2: Update the metadata description**

In the same `metadata` property description (currently lines ~342-348), remove `callbackSecret?` from the list of keys. The text currently reads:

```
                    DEPRECATED. Legacy JSON envelope with profileId?, profileOverrides?,
                    reports[]?, callbackUrl?, callbackSecret?, callbackAlgorithm?.
```

Change to:

```
                    DEPRECATED. Legacy JSON envelope with profileId?, profileOverrides?,
                    reports[]?, callbackUrl?, callbackAlgorithm?.
```

- [ ] **Step 3: Add callbackSecret to JobAcceptedResponse**

Find the `JobAcceptedResponse` schema in `components/schemas`. Add a new optional property:

```yaml
    JobAcceptedResponse:
      type: object
      properties:
        jobId:
          type: string
          format: uuid
        status:
          type: string
          example: PENDING
        callbackSecret:
          type: string
          description: >-
            Server-generated HMAC shared secret for verifying callbacks. Present only when
            callbackUrl was supplied. Returned exactly once at creation; never exposed again.
          example: svcbk_<base64url>
```

(If the schema already has `jobId`/`status`, only add the `callbackSecret` property — do not duplicate the others.)

- [ ] **Step 4: Lint the YAML**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('src/main/resources/openapi/openapi.yaml')); print('ok')"`
Expected: `ok` (valid YAML). If python3/yaml unavailable, eyeball the indentation against the surrounding entries.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/openapi/openapi.yaml
git commit -m "docs(api): drop input callbackSecret, document server-generated secret in 202"
```

---

## Task 6: Full-suite regression + verification

**Files:** none

- [ ] **Step 1: Run the entire test suite**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -B -ntp verify`
Expected: BUILD SUCCESSFUL, zero failures. Pay attention to:
- `CallbackWorkerTest` — must pass unchanged (the worker is untouched).
- `AuthenticationIT`, `ApiKeyServiceTest`, `AuditServiceTest` — must pass unchanged.
- Any IT that submits an async job (`AsyncVerificationControllerTest`, integration tests).

- [ ] **Step 2: If any test still references the removed field, fix it**

Run: `git grep -n "callbackSecret" -- 'src/**/*.java'`
Expected: only references to `callbackSecretCipher` (the persisted column on `ValidationJob`) and the adapter's new `generateCallbackSecret()`. Any remaining `req.callbackSecret()`, `parts.callbackSecret()`, or `.callbackSecret(` on a `SubmitRequest`/`VerificationRequestParts` is a missed callsite — fix it.

- [ ] **Step 3: Manual smoke (optional, if Docker available)**

Build and run the image per `docs/02b-docker-operational-guide.md` and submit an async job WITHOUT `callbackSecret`, then WITH a `callbackSecret` part, confirming the 202 carries the secret in case 1 and a 400 in case 2. Skip if no Docker in the environment.

- [ ] **Step 4: Final commit if any stray references were fixed in Step 2**

```bash
git add -A
git commit -m "test: fix remaining callbackSecret references"
```

(Only if Step 2 found anything. Otherwise skip — no empty commits.)

- [ ] **Step 5: Push the branch and open a PR**

```bash
git push -u origin feat/server-generated-callback-secret
gh pr create --base develop --title "feat: server-generated callback secret" \
  --body "Implements docs/2026-07-12-server-generated-callback-secret-design.md. Breaking change: callbackSecret removed from request input (400 if present); generated server-side and returned once in the 202 body."
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- API contract (remove input, 202 body secret, both-path 400) → Task 3 (controller + parts) + Task 5 (OpenAPI). ✓
- `SecretGeneratorPort` + adapter → Task 1. ✓
- `svcbk_` + 256-bit base64url format → Task 1 tests + impl. ✓
- `AsyncJobService` new `SubmitResult`, drop input field, generate when callbackUrl present → Task 2. ✓
- `VerificationRequestParts.fromMetadata` rejects key → Task 3 step 3c. ✓
- Controller rejects multipart part → Task 3 step 7e. ✓
- No DB migration → no task (correctly absent). ✓
- `CallbackWorker` unchanged → verified in Task 6 step 1. ✓
- Collateral: golden test + fixture → Task 4. ✓
- TDD outside-in ordering (adapter → service → controller → collateral → OpenAPI) → Tasks 1→2→3→4→5. ✓

**Placeholder scan:** none — every step has concrete code or an exact command.

**Type consistency:**
- `SubmitResult(UUID jobId, String callbackSecret)` — used identically in Task 2 (defines it), Task 3 (consumes it in controller and tests). ✓
- `SubmitRequest` 7-component form — used identically in Task 2 (defines it) and Task 3 (controller builds it). ✓
- `VerificationRequestParts.from(...)` 8-arg form — Task 3 step 3b defines it; step 1c updates all callsites. ✓
- `generateCallbackSecret()` — Task 1 defines, Task 2 consumes. ✓

**One known judgment call:** Task 3 step 7e uses `request instanceof MultipartHttpServletRequest` with a raw `HttpServletRequest` parameter. An alternative is a dedicated `@RequestHeader` or a filter, but the inline instanceof check is the smallest change and keeps the rejection logic co-located with the endpoint. The controller test (step 5c) exercises this path with a real `MockMultipartFile`, so it is covered.
