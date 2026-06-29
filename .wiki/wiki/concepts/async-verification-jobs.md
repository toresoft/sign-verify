---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
volatility: warm
---

# Async verification jobs

The asynchronous verification path in [[entities/sign-verify-2]] for large documents or webhook delivery. Backed by the persisted `validation_job` table with an explicit state machine, a validation worker, and an HMAC-signed callback dispatcher.

## State machine
```
PENDING ──► RUNNING ──► COMPLETED ──► DELIVERED ──► DELETED
                │             └──► DELIVERY_FAILED ──► DELETED
                └──► FAILED ─────────────────────────► DELETED
```
`JobStatus`: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `DELIVERED`, `DELIVERY_FAILED`, `DELETED` (tombstone; `original_status` records the prior value).

## Workers
- **ValidationWorker** polls (default 5s) for `PENDING` jobs; **skips the cycle if the DSS [[concepts/circuit-breaker]] is OPEN**.
- **Callback dispatch** polls `FOR UPDATE SKIP LOCKED` (default 10s, ShedLock-locked) for `COMPLETED`/`FAILED` jobs with a `callback_url`; HMAC-signs the body (default `HmacSHA256`); classifies responses into success (2xx) / non-retryable (4xx excl. 408/425/429) / retryable, with exponential backoff up to `max-attempts`.

## Security & ownership
- Callback secret encrypted at rest (**AES-256-GCM**) with the master key.
- Result retrieval `GET /api/v1/verifications/jobs/{jobId}` — owner or PRIVILEGED only (else `404`); `DELETED` → `410 Gone`.

## Related
- [[entities/sign-verify-2]] · [[concepts/circuit-breaker]] · [[concepts/shedlock]]
- [[concepts/validation-profiles]] · [[concepts/signature-validation]]
