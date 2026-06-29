---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
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

## Related
- [[concepts/async-verification-jobs]] · [[entities/validation_job]]
- [[concepts/shedlock]] · [[entities/sign-verify-2]]
