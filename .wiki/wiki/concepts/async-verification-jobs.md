---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-07-03
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

## Hardening (2026-07-03, tag 0.12.3)
- `ValidationJob` has an **optimistic lock** (`@Version`, migration V4); bulk JPQL updates must bump it explicitly (`version = version + 1`) or the lock protects nothing.
- Expiry is a **conditional bulk UPDATE** (`expireOverdue`: only `PENDING`/`RUNNING` past `expires_at`) — a terminal job can never be clobbered back to FAILED by the cleanup racing a worker. The picker also skips already-expired jobs.
- **Stale-RUNNING sweep** on every worker poll (`resetStaleRunning`, threshold `app.async.worker.stale-running-timeout`, default 30m): recovers jobs stranded by a crash between the atomic claim and the outcome write. Needed because `process()`/`dispatch()` are deliberately **not** transactional (self-invocation makes the annotation dead anyway; a DB tx across a minutes-long DSS validation or an HTTP call would be worse).
- `dss.unavailable` requeue gives back the claim's `pickup_attempts` increment: transient DSS outage no longer consumes the poison-job budget (bounded by job TTL + circuit breaker).
- Callback HMAC algorithm now validated at submit against `app.callback.allowed-algorithms` (the yaml key existed but was never bound — "documented ≠ enforced"). (From [[raw/notes/2026-07-03-ll-code-review-fixes-hardening]].)
- **Never `save()` the entity loaded at claim time to write the outcome.** A polling `GET /jobs/{id}` that stamps `last_accessed_at` via `setX + save` bumps `@Version`, so the worker's final `save` after a minutes-long validation throws `StaleObjectStateException` and the result is lost (job hangs RUNNING until the stale sweep). Fix: version-neutral bulk touch for the stamp, and conditional bulk finalize (`markCompleted`/`markFailed`/`requeueTransient`, keyed on `status=RUNNING`) for the outcome. (From [[raw/notes/2026-07-14-ll-async-job-race-budget-phase]].)
- **Per-job wall-clock budget is enforced per-instance in memory**, not by a DB sweep: a `WHERE status=RUNNING AND started_at<cutoff` sweep cannot tell "slow but alive on another instance" from "crashed", so it would kill live jobs. Each instance times out only its own in-flight jobs (`job.timeout`, no retry) via an in-memory deadline map + short watchdog; the 30m stale sweep remains only for crash recovery. Requires `max-validation-time < stale-running-timeout`. (From [[raw/notes/2026-07-14-ll-async-job-race-budget-phase]].)
- **A conditional finalize that no-ops must roll back its side effects.** On budget-timeout the worker had already written `<jobId>/result.json`, but `markCompleted` returned 0 so `result_path` stayed null — the cleanup scheduler's `delete(result_path)` = `delete(null)` leaked the blob forever. Delete the just-written file on the 0-rows branch. (From [[raw/notes/2026-07-14-ll-async-job-race-budget-phase]].)
- **Phase observability** (`phase`/`phase_updated_at`, exposed via `GET /jobs/{id}`) is stamped **unconditionally** at each phase boundary via a version-neutral bulk UPDATE: a gate keyed on elapsed-time-at-entry can never stamp the phase a job actually hangs in (it accrues its duration after entry). (From [[raw/notes/2026-07-14-ll-async-job-race-budget-phase]].)

## Security & ownership
- Callback secret encrypted at rest (**AES-256-GCM**) with the master key.
- Result retrieval `GET /api/v1/verifications/jobs/{jobId}` — owner or PRIVILEGED only (else `404`); `DELETED` → `410 Gone`.

## Related
- [[entities/sign-verify-2]] · [[concepts/circuit-breaker]] · [[concepts/shedlock]]
- [[concepts/validation-profiles]] · [[concepts/signature-validation]]
