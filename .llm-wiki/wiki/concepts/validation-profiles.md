---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
---

# Validation profiles

The sign-verify-2 abstraction over a **DSS validation policy** (policy XML). A profile bundles the constraints applied during [[concepts/signature-validation]] and is stored in the `verification_profile` table.

## Three layers of customization
1. **Presets (built-in)** — e.g. `STANDARD`; sensible defaults for common cases.
2. **Custom profiles** — admin-defined policies persisted in the DB (`is_default` flag marks the fallback).
3. **Per-request overrides** — `profileOverrides` JSON on a single verification call, applied on top of the selected profile (`overridesApplied: true` in the response).

## The `Level` attribute (key concept)
The DSS policy XML's `Level` attribute drives which constraint groups are evaluated (basic / long-term / archival). See [[sources/SRC-2026-06-27-003]] §4.2 "Policy XML format". This is the central knob mapping to [[concepts/baseline-profiles]].

## Lifecycle
- Created/listed/updated via the **Profiles** API tag ([[sources/SRC-2026-06-27-008]]).
- Selected per verification via `metadata.profileId`; omitted → default profile.
- The verification response echoes `profileUsed` so callers can audit what was applied.

## Related
- [[entities/dss]] · [[concepts/signature-validation]] · [[concepts/baseline-profiles]]
- [[entities/sign-verify-2]] · [[concepts/design-first-openapi]]
