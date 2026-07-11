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

## Related
- [[entities/sign-verify-2]] · [[concepts/design-first-openapi]]
- [[concepts/structured-logging]] / observability ([[sources/SRC-2026-06-27-007]])
- [[2026-07-01-ll-extraction-recursive-unwrap]]
- [[raw/notes/2026-07-11-ll-boot4-migration-review]]
