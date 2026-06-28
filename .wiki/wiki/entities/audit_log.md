---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-007
---

# audit_log (table)

The append-only audit table in [[entities/sign-verify-2]]. Written by `AuditService`; queryable via `GET /api/v1/audit-log` (**PRIVILEGED** only).

## Record shape (`AuditLog`)
| Field | Description |
|-------|-------------|
| `id` | UUID |
| `occurredAt` | event instant |
| `principalType` / `principalId` | actor (or `SYSTEM`) |
| `action` | action string |
| `targetType` / `targetId` | affected resource |
| `success` | boolean outcome |
| `details` | free-form JSON |
| `ipAddress` | caller IP |

Indexed on `occurred_at`, `principal_id`, `action`. Query filters: `principalId`, `action`, `from`/`to`, `targetType`/`targetId`, `success`, paging; results sorted by `occurredAt` desc.

> ⚠️ **Implementation gap:** the table, `AuditService`, and read API exist, but `AuditService` is **not yet wired** into operational paths (verification, key management, TSL refresh) — the table may be empty until the `wire-audit-log` plan lands. See [[sources/SRC-2026-06-27-007]] §6.3.

## Related
- [[entities/sign-verify-2]] · [[concepts/structured-logging]]
- [[entities/api_key]] · [[concepts/trusted-lists]]
