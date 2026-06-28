---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
---

# ShedLock

A library providing **distributed scheduler locking** so that scheduled tasks run on **exactly one instance** in a multi-node deployment. Used by [[entities/sign-verify-2]] to coordinate jobs that must not double-execute across replicas.

## Uses in sign-verify-2
- **TSL refresh scheduler** — only one instance performs a [[concepts/tsl-hot-swap-refresh]] (`@SchedulerLock`).
- **Callback dispatch worker** — single dispatcher polls `FOR UPDATE SKIP LOCKED` for callback delivery.
- **Cleanup/retention schedulers** — single execution of expired-job cleanup.

Backed by the `shedlock` table (managed by the library). Each lock has a name (e.g. `callbackDispatch`).

## Related
- [[entities/sign-verify-2]] · [[concepts/tsl-hot-swap-refresh]] · [[concepts/async-verification-jobs]]
- [[concepts/trusted-lists]]
