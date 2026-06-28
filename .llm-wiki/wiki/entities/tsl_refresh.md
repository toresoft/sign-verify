---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-004
---

# tsl_refresh (table)

The **refresh history** table in [[entities/sign-verify-2]] — one row per [[concepts/tsl-hot-swap-refresh]] run. Lets operators audit how trust material evolved over time.

## Columns
- `id` (UUID PK), `trigger` (`SCHEDULED` | `MANUAL` | `STARTUP`).
- `triggered_by_principal_type` / `triggered_by_principal_id` (nullable; null for `STARTUP`/`SCHEDULED`).
- `started_at`, `completed_at`, `status` (`RUNNING` | `SUCCESS` | `PARTIAL` | `FAILED`).
- `sources_total`, `sources_failed`, `certificates_added`, `certificates_removed`, `certificates_unchanged`.
- `error_summary` (nullable).

Queried indirectly via `GET /api/v1/tsl/status` (last refresh, per-source outcome, cert counts). Force-refresh is **PRIVILEGED**.

## Related
- [[concepts/tsl-hot-swap-refresh]] · [[entities/trusted_certificate]]
- [[concepts/shedlock]] · [[entities/dss-tsl-adapter]]
