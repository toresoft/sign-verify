# Server-generated callback secret

**Date:** 2026-07-12
**Status:** Approved (pending implementation)
**Related review:** HMAC security review — item "#2 secret restituito invece che passato nella chiamata"

## Problem

`POST /api/v1/verifications/async` lets the client supply `callbackSecret` as a multipart part
(or inside the legacy `metadata` JSON envelope). The service stores it AES-256-GCM encrypted and
later uses it to HMAC-sign outbound callbacks.

The security review flagged that the service applies no strength policy to the client-supplied
secret: any non-blank string is accepted, including a single character. A weak shared secret makes
the HMAC signature forgeable by anyone who can observe the callback body and the timestamp/nonce
headers (which travel in clear). This is the classic weakness of a client-supplied shared secret.

## Solution (decision summary)

Adopt the Stripe/GitHub webhook-secret model: the **server generates** the secret at job-submission
time, stores it encrypted, and returns the plaintext **once** in the 202 response. The client never
supplies the secret.

Decisions locked in during brainstorming:

1. **Breaking change, clean cut.** `callbackSecret` is removed from request input (multipart parts
   and legacy `metadata`). A client that still sends it gets `400`. No deprecation shim.
2. **Secret returned in the 202 response body**, alongside `jobId`/`status`.
3. **Format:** `svcbk_` prefix + 256-bit entropy encoded as Base64URL without padding
   (~49 chars total).
4. **Generation lives behind a `SecretGeneratorPort`** in the domain, with a `SecureRandom`-based
   adapter — same hexagonal style as `SecretCipherPort` / `PasswordHasherPort`.

## API contract

### Request — `POST /api/v1/verifications/async` (multipart/form-data)

Unchanged parts: `file`, `profile`, `profileId` (deprecated), `profileOverrides`, `reports`,
`callbackUrl`, `callbackAlgorithm`, `metadata` (deprecated).

**Removed:** `callbackSecret`. Its presence is rejected on **both** input paths:

- **Multipart part:** Spring silently drops multipart parts that are not bound to any
  `@RequestPart`, so the controller must explicitly check the incoming multipart request for a
  `callbackSecret` part and, if present, reject with `400 invalid-request`.
- **Legacy `metadata` JSON envelope:** `VerificationRequestParts.fromMetadata` already parses the
  JSON, so it checks for a `callbackSecret` key and, if present, rejects with `400
  invalid-request`.

Rejecting on both paths (rather than silently ignoring on `metadata`) is required for correctness:
ignoring it would make the server generate a different secret from the one the caller expects,
silently breaking callback verification for legacy clients. A loud `400` tells the caller to
migrate. This matches the "clean breaking change" decision applied uniformly.

Semantics of `callbackUrl` are unchanged: when present, the job is eligible for callback delivery;
when absent, no callback is attempted. The difference is that the secret is now **derived** from the
presence of `callbackUrl`, not supplied by the caller.

### Response — `202 Accepted`

```json
{
  "jobId": "00000000-0000-0000-0000-000000000000",
  "status": "PENDING",
  "callbackSecret": "svcbk_<base64url>"
}
```

- `callbackSecret` is present **only** when `callbackUrl` was supplied. Otherwise the key is omitted
  entirely (not serialized as `null`).
- The plaintext secret is exposed exactly once, here. It is never returned again by any endpoint,
  never written to logs, and never written to the audit log details.

### OpenAPI changes (`src/main/resources/openapi/openapi.yaml`)

- Remove `callbackSecret` from the `/api/v1/verifications/async` multipart schema.
- Update the `metadata` description text to drop `callbackSecret` from the listed keys.
- Add optional `callbackSecret` (type `string`, nullable absent) to the `JobAcceptedResponse`
  schema, with a description noting it is returned only when `callbackUrl` was supplied and is
  shown once.

## Domain and persistence

### New port — `SecretGeneratorPort`

Package `org.toresoft.signverify.domain.port`:

```java
public interface SecretGeneratorPort {
  /** Generates a fresh callback secret, e.g. "svcbk_<base64url>". */
  String generateCallbackSecret();
}
```

The domain defines the contract only; it does not depend on `java.security`.

### New adapter — `RandomSecretGeneratorAdapter`

Package `org.toresoft.signverify.adapter.crypto` (alongside `AesGcmSecretCipherAdapter`):

- A single reused `SecureRandom`.
- Draws 32 bytes (256-bit entropy), Base64URL-encodes without padding, prepends `svcbk_`.
- Total length ~49 chars.

### `AsyncJobService.submit()` changes

- New dependency: `SecretGeneratorPort secretGenerator` (constructor-injected).
- `SubmitRequest` **loses** the `callbackSecret` field.
- New return type: `record SubmitResult(UUID jobId, String callbackSecret)`. `callbackSecret` is
  the plaintext (returned to the controller for the 202 body); it is `null` when no `callbackUrl`.
- Flow:
  1. Backpressure checks (unchanged).
  2. `callbackUrl` validation (unchanged).
  3. `callbackAlgorithm` allowlist check (unchanged).
  4. If `callbackUrl != null`: `String secret = secretGenerator.generateCallbackSecret();`
     `j.setCallbackSecretCipher(cipher.encrypt(secret));` — the generated plaintext is what gets
     encrypted and returned.
  5. If `callbackUrl == null`: no generation, no encryption, cipher column stays `null`.
- The removed `callbackSecret is required when callbackUrl is provided` check is deleted: the
  invariant is now structural, not validated from input.

### Persistence — no migration

`validation_job.callback_secret_cipher` already exists and keeps the same semantics (AES-256-GCM
ciphertext of the shared secret). It is now fed by the generated secret instead of the client
secret. **No new `V7__*.sql` is required.**

### `AsyncVerificationController.submit()` changes

- Drop the `@RequestPart("callbackSecret")` parameter.
- After binding, explicitly reject any request that still carries a `callbackSecret` multipart
  part: read `req.getMultiPartMap().get("callbackSecret")` (or the equivalent via
  `MultipartHttpServletRequest`) and, if non-empty, throw `AppException.badRequest(...)`. This
  implements the loud breaking-change enforcement on the parts path.
- Map the new `SubmitResult` into the 202 body: include `callbackSecret` only when non-null.

### `VerificationRequestParts.fromMetadata` changes

- After parsing the legacy `metadata` JSON envelope, reject it if it contains a `callbackSecret`
  key (present and non-null): throw `AppException.badRequest(...)`. This is the enforcement point
  for the `metadata` path, and keeps the rejection logic in the same place that already parses the
  envelope.
- Remove the now-unused `callbackSecret` parameter and field from the record and the `from(...)`
  factory.

### `CallbackWorker` — unchanged

`deliver()` continues to do `cipher.decrypt(job.getCallbackSecretCipher())` and pass the plaintext
to the dispatcher/`HmacSigner`. No code change.

## Testing strategy (TDD, outside-in)

Red → green → refactor per unit. Order:

1. **`RandomSecretGeneratorAdapterTest` (new)**
   - `generate_startsWithSvcbkPrefix`
   - `generate_decodesTo256Bits` — Base64URL-decodable payload is 32 bytes.
   - `generate_producesUniqueValues` — N consecutive calls all distinct.
   - `generate_isBase64UrlWithoutPadding` — no `=` suffix, valid charset.

2. **`AsyncJobServiceTest` (rewrite callback section)**
   - `submit_generatesAndEncryptsSecretWhenCallbackUrlPresent` — verifies
     `secretGenerator.generateCallbackSecret()` is called, its return value is passed to
     `cipher.encrypt(...)`, and the ciphertext lands on the job.
   - `submit_returnsGeneratedSecretInResult` — `SubmitResult.callbackSecret()` equals the generated
     plaintext (not the ciphertext).
   - `submit_omitsSecretWhenNoCallbackUrl` — generator and cipher never invoked, job cipher column
     `null`, result secret `null`.
   - Removed tests: `submit_callbackUrlWithoutSecret_isRejected`,
     `submit_callbackUrlWithBlankSecret_isRejected`, `submit_encryptsCallbackSecret` (logic is
     inverted: the service now produces, not consumes, the secret).

3. **`AsyncVerificationControllerTest` (rewrite callback assertions)**
   - `submit_returnsCallbackSecretInBody_whenCallbackUrlPresent` — 202 body contains
     `callbackSecret` with the `svcbk_` prefix.
   - `submit_omitsCallbackSecretInBody_whenNoCallbackUrl` — body has no `callbackSecret` key.
   - `submit_callbackSecretPartIsRejected_with400` — breaking-change enforcement on the multipart
     parts path.
   - `submit_callbackSecretInMetadataIsRejected_with400` — breaking-change enforcement on the
     legacy `metadata` envelope (a `metadata` JSON carrying `callbackSecret` returns 400).

4. **Collateral test fixes (references removed `callbackSecret` field)**
   - `VerificationRequestPartsTest` — `fromMetadata_rejectsCallbackSecretKey` (legacy envelope
     path explicitly rejects a `callbackSecret` key with `400`); drop the existing
     `callbackSecret` assertions on the parts path (field removed).
   - `PersistedJsonRereadGoldenTest` — remove the `callbackSecret` golden value (the persisted job
     payload no longer carries a client secret).
   - Any other test constructing `SubmitRequest` with the old arity is updated.

5. **OpenAPI** — updated by hand; verified during review (no automated conformance test exists
   today; the spec is the contract).

### Not under test here

- `CallbackWorkerTest` stays green unchanged (the worker is untouched).
- `ValidationJobRepositoryTest` does not touch the secret and stays valid.
- Integration tests that submit jobs are updated only if they pass `callbackSecret`.

## File impact

### New

- `src/main/java/org/toresoft/signverify/domain/port/SecretGeneratorPort.java`
- `src/main/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapter.java`
- `src/test/java/org/toresoft/signverify/adapter/crypto/RandomSecretGeneratorAdapterTest.java`

### Modified

- `src/main/java/org/toresoft/signverify/application/AsyncJobService.java`
- `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`
- `src/main/java/org/toresoft/signverify/api/VerificationRequestParts.java`
- `src/main/resources/openapi/openapi.yaml`
- `src/test/java/org/toresoft/signverify/application/AsyncJobServiceTest.java`
- `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java`
- `src/test/java/org/toresoft/signverify/api/VerificationRequestPartsTest.java`
- `src/test/java/org/toresoft/signverify/application/PersistedJsonRereadGoldenTest.java`

### Unchanged (verified)

- `AesGcmSecretCipherAdapter`, `CallbackWorker`, `HmacCallbackDispatcherAdapter`, `HmacSigner`,
  `ApiKeyService`, security package, all DB migrations.

## Build sequence (each step leaves the build green)

1. `SecretGeneratorPort` + `RandomSecretGeneratorAdapter` + test
   → `./gradlew test --tests '*RandomSecretGeneratorAdapterTest'`
2. `AsyncJobService` refactor (new `SubmitResult`, inject port, drop input field) + test rewrite
   → `./gradlew test --tests '*AsyncJobServiceTest'`
3. `VerificationRequestParts` (drop field) + `AsyncVerificationController` + test rewrite
   → `./gradlew test --tests '*AsyncVerificationControllerTest'`
4. Collateral test fixes (`VerificationRequestPartsTest`, `PersistedJsonRereadGoldenTest`)
5. `openapi.yaml` update
6. Full suite → `./gradlew test`

## Residual risk / out of scope

- The DNS-rebinding SSRF (#1), algorithm re-validation at dispatch (#3), and `String`-in-heap
  hardening (#4) from the review are **not** addressed here; tracked separately.
- Secrets remain readable in heap as `String` until GC; switching to zeroed `char[]`/`byte[]` is
  deferred to the hardening pass.
