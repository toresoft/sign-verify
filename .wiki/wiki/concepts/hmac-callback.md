---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-07-13
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
volatility: warm
---

# HMAC callback

The webhook delivery mechanism for [[concepts/async-verification-jobs|async verification]] results in [[entities/sign-verify-2]]. When a job reaches `COMPLETED`/`FAILED` and has a `callback_url`, the dispatch worker POSTs the result JSON with an HMAC signature.

## Mechanics (design §8.4)
- Algorithm default `HmacSHA256`; the **callback secret is encrypted at rest** (AES-256-GCM) with the master key (`app.security.master-key`).
- Signature computed over the request body and sent in a header for the receiver to verify.
- **Response classification:** success (2xx) → `DELIVERED`; non-retryable (4xx excl. 408/425/429) → `DELIVERY_FAILED`; retryable/network error → `callback_attempts++` + exponential backoff up to `max-attempts` → then `DELIVERY_FAILED`.

Dispatch polls `SELECT … FOR UPDATE SKIP LOCKED` (default 10s, [[concepts/shedlock|ShedLock]]-locked) so multiple instances don't double-deliver.

## Server-generated secret (2026-07-13)
The callback secret is no longer client-supplied — it is generated server-side
(`SecureRandom`, 256-bit, `svcbk_`-prefixed base64url) and returned exactly once in the
202 job-submission body, never re-exposed on `GET /jobs/{id}` or in audit logs. Legacy
client-supplied `callbackSecret` is rejected with 400 on both input paths (dedicated
multipart part and the deprecated `metadata` JSON envelope).

**Form-field bypass (found in review, fixed same day)**: the initial rejection checked only
`MultipartHttpServletRequest.getMultiFileMap().containsKey("callbackSecret")`, which catches
parts sent WITH a `filename=` attribute but misses a `callbackSecret` sent as a plain form
field (no filename, e.g. `curl -F callbackSecret=shh`) — that lands in the parameter map and
returned 202. The smuggled value was inert (no longer bound anywhere), so this was a contract
gap, not a secret-override risk. **Resolution** (`a2e2aae`): the check now covers both the
file map and the parameter map, and the rejection message is centralized in
`VerificationRequestParts.CALLBACK_SECRET_REJECTED_MSG`. The originally-suggested fix
(`@RequestPart(required=false)`) was tested empirically and does NOT catch both encodings
under Spring Boot 4.1 — the dual-map check is the robust solution. See
[[raw/notes/2026-07-13-ll-callback-secret-review]] for the full analysis.

## Serialization-library migration risk
Because the HMAC signature is computed over the exact serialized bytes, this is a **wire
format boundary that migration reviews miss by default** — a controller-focused review
checks HTTP request/response shapes but not outbound callback payloads. A Jackson 2→3
migration plan for [[entities/sign-verify-2]] added a dedicated golden pin for the
callback payload shape after an independent review caught the gap; see
[[raw/notes/2026-07-11-ll-boot4-migration-review]]. Any future serialization change here
needs the same pin: capture the exact JSON pre-change, assert it byte-for-byte post-change.

## Related
- [[concepts/async-verification-jobs]] · [[entities/validation_job]]
- [[concepts/shedlock]] · [[entities/sign-verify-2]]
