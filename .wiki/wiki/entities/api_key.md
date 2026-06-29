---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-005
volatility: warm
---

# api_key (table)

The table backing [[entities/sign-verify-2]]'s always-on API-key authentication ([[concepts/api-key-authentication]]). One row per key.

## Shape & invariants
- **Format:** the plaintext key is `sv_<prefix>_<body>`. The `<prefix>` is **indexed + unique** for O(1) lookup; the full key is **bcrypt-hashed** at rest.
- **Returned once:** the plaintext is shown **only at creation** (`POST /api/v1/api-keys`); never retrievable afterward.
- **Flags:** `enabled`, `expires_at`, role (`PRIVILEGED` / standard).
- **Last-privileged-key invariant:** the last enabled PRIVILEGED key **cannot be removed or disabled** — guarantees admin access is never locked out (delete → `409`).

## Bootstrap
On first startup, if no key exists, a PRIVILEGED bootstrap key is generated and written to `app.security.bootstrap-key-file` (mode 0600).

## Related
- [[entities/sign-verify-2]] · [[concepts/problemjson]] · [[concepts/design-first-openapi]]
- [[entities/validation_job]] (ownership via `requested_by_principal_*`)
