---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
---

# validation_job (table)

The table backing [[concepts/async-verification-jobs]] in [[entities/sign-verify-2]]. One row per asynchronous verification request.

## Key columns
- `id` (UUID PK), `status` (`PENDING`/`RUNNING`/`COMPLETED`/`FAILED`/`DELIVERED`/`DELIVERY_FAILED`/`DELETED`).
- `original_status` — value of `status` before tombstone (set when `status=DELETED`).
- `profile_id` (FK, nullable), `profile_overrides` (JSON), `reports_requested` (csv e.g. `simple,etsi`).
- `document_path` / `document_filename` / `result_path` — filesystem job storage.
- `callback_url`, `callback_secret_cipher` (AES-256-GCM), `callback_algorithm` (default `HmacSHA256`), `callback_attempts`, `next_callback_at`, `last_callback_error`, `pickup_attempts`.
- Timestamps: `created_at`, `started_at`, `completed_at`, `delivered_at`, `expires_at`, `deleted_at`, `last_accessed_at`.
- Ownership: `requested_by_principal_type` (`API_KEY` | `OAUTH_USER`), `requested_by_principal_id`.

## Indexes
- `(status, next_callback_at)` — callback-dispatch polling.
- `(status, pickup_attempts)` — validation-worker polling.
- `(status, expires_at)` — expired-job cleanup.
- `(requested_by_principal_type, requested_by_principal_id, status)` — ownership filtering (GET returns `404` for non-owners).

## Related
- [[concepts/async-verification-jobs]] · [[concepts/circuit-breaker]] · [[concepts/shedlock]]
- [[entities/api_key]] · [[entities/verification_profile]] · [[entities/sign-verify-2]]
