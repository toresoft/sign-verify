---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-07-11
verified: 2026-07-01
confidence: high
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-007
  - sources/SRC-2026-06-27-008
  - raw/notes/2026-07-01-ll-extraction-recursive-unwrap
volatility: warm
---

# problem+json (RFC 9457)

The error response format used by [[entities/sign-verify-2]]: `application/problem+json` (RFC 9457). All errors emitted via `GlobalExceptionHandler`; each carries a stable `type` URN `urn:signverify:error:<code>` so clients can branch reliably.

## Catalogued codes (examples)
- `auth.missing-credentials` → 401
- `excessive-load.concurrency` → 429 (sync semaphore exhausted)
- `unknown report type` / `invalid metadata json` → 400
- Job lifecycle: `410 Gone` (deleted job), `404` (non-owner job access), `409` (last privileged key).

The full catalog lives in the design spec §11 ([[sources/SRC-2026-06-27-002]]).

## Why
Stateless API + design-first contract ([[concepts/design-first-openapi]]) need machine-parseable, stable, self-describing errors. RFC 9457 problem details are the standard shape; the URN `type` field gives durable, documented codes independent of HTTP status.

## Gotcha: `getMessage()` è il title, non il detail
`AppException extends RuntimeException` con `super(title)` → `getMessage()` restituisce **sempre** il
title RFC 9457 (es. `"Bad Request"`), mentre il testo descrittivo vive in un campo separato
`detail` esposto da `getDetail()`. Nei test **asserire sul detail** (`thrown.getDetail()`),
mai su `hasMessageContaining(...)`. Vedi L3 in [[2026-07-01-ll-extraction-recursive-unwrap]].

## Coverage gap: `HttpMessageNotReadableException` had no end-to-end test
`GlobalExceptionHandlerTest` unit-tests `handleApp`/`handleGeneric`/conflict mapping
directly against the handler class, but as of 2026-07-11 no test drove a real malformed
JSON body through the full MockMvc/HTTP stack to confirm `handleNotReadable` (400,
`validation.invalid-input`) actually fires end-to-end. This matters for any change to the
JSON deserialization layer (e.g. a Jackson major-version migration): a converter-level
regression can silently degrade this path to `500 internal-error` and only a real request
would show it. Flagged as a **critical, no-question-asked** regression-test requirement
ahead of the Boot 4.1/Jackson 3 migration — see
[[raw/notes/2026-07-11-ll-boot4-migration-review]].

## Field-level validation: `invalid-params` (RFC 9457 §3.1)
Validation failures carry an `invalid-params` array (`[{name, reason}]`), one entry per rejected
field, so clients can bind the error to a specific input instead of parsing the flat `detail`.
Four sources feed it: `MethodArgumentNotValidException` (body bean validation → `getFieldErrors()`),
`HttpMessageNotReadableException` (body type/enum mismatch), `MethodArgumentTypeMismatchException`
(path/query coercion, e.g. bad UUID or non-numeric `page`), and `HandlerMethodValidationException`
(method-level `@RequestParam`/`@PathVariable`/`@RequestPart` constraints). For the malformed-body
case, resolve the field from the base `tools.jackson.core.JacksonException.getPath()` — **not**
`MismatchedInputException`: the OpenAPI-generated enum `@JsonCreator` throws `IllegalArgumentException`,
which Jackson 3 wraps as a broader `JacksonException`, so the narrow subclass check misses bad-enum
bodies. A pure syntax error has an empty path → `"malformed request body"`.

The param handlers matter beyond aesthetics: before them, a path/query type-mismatch
(`page=abc`, non-UUID `{id}`) had **no** handler and fell through to the catch-all → `500
internal-error`, contradicting the documented "bad query param → 400". Any body-shape change must
also update the OpenAPI `Problem` schema and be covered by a full-stack MockMvc test. See
[[raw/notes/2026-07-12-ll-rfc9457-invalid-params]].

## Related
- [[entities/sign-verify-2]] · [[concepts/design-first-openapi]]
- [[concepts/structured-logging]] / observability ([[sources/SRC-2026-06-27-007]])
- [[2026-07-01-ll-extraction-recursive-unwrap]]
- [[raw/notes/2026-07-11-ll-boot4-migration-review]]
