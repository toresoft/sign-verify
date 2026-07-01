---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-07-01
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

## Related
- [[entities/sign-verify-2]] · [[concepts/design-first-openapi]]
- [[concepts/structured-logging]] / observability ([[sources/SRC-2026-06-27-007]])
- [[2026-07-01-ll-extraction-recursive-unwrap]]
