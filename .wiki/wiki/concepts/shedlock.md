---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-07-23
sources:
  - sources/SRC-2026-06-27-002
  - notes/2026-07-23-docs-storage-s3-horizontal-scaling
volatility: warm
---

# ShedLock

A library providing **distributed scheduler locking** so that scheduled tasks run on **exactly one instance** in a multi-node deployment. Used by [[entities/sign-verify-2]] to coordinate jobs that must not double-execute across replicas.

## Uses in sign-verify-2
- **TSL refresh scheduler** — only one instance performs a [[concepts/tsl-hot-swap-refresh]] (`@SchedulerLock`).
- **Callback dispatch worker** — `@SchedulerLock(name = "callbackDispatch")` makes a single instance poll for due callbacks; each one is then taken with a conditional claim (`claimCallback` parks `next_callback_at` into the future) so the dispatch itself can be offloaded to an executor without holding the lock for the whole HTTP call. **Correction (2026-07-23): this is not `FOR UPDATE SKIP LOCKED`** — that phrase was wrong in an earlier revision of this page; `SKIP LOCKED` appears nowhere in the codebase.
- **Cleanup/retention schedulers** — single execution of expired-job cleanup.

Backed by the `shedlock` table (managed by the library), which lives in the shared PostgreSQL
schema — that is what makes these duties safe across replicas.

| Lock name | Duty | Default schedule |
|---|---|---|
| `tslRefresh` | Trusted Lists refresh | cron `0 0 2 * * *` |
| `jobCleanup` | Expired job + document cleanup | cron `0 30 3 * * *` |
| `callbackDispatch` | HTTP callback dispatch | poll every 10 s |

Only the pod that acquires the lock runs the duty; if it dies, the lock expires after
`lockAtMostFor` and another pod takes over. Note that the async **job claim** deliberately does not
use ShedLock — it is a DB conditional UPDATE (see [[concepts/async-verification-jobs]]).
(Documented in `docs/{en,it}/08`, from [[raw/notes/2026-07-23-docs-storage-s3-horizontal-scaling]].)

## Related
- [[entities/sign-verify-2]] · [[concepts/tsl-hot-swap-refresh]] · [[concepts/async-verification-jobs]]
- [[concepts/document-storage-backends]]
- [[concepts/trusted-lists]]
