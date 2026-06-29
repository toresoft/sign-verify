---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
volatility: warm
---

# verification_profile (table)

The table storing [[concepts/validation-profiles]] in [[entities/sign-verify-2]]. Each row wraps a DSS validation policy (policy XML) and the constraints applied during [[concepts/signature-validation]].

## Shape
- Bundles a DSS policy; the policy XML's `Level` attribute selects constraint groups aligned to [[concepts/baseline-profiles]] (basic / long-term / archival).
- `is_default` — marks the fallback profile used when a verification omits `profileId`.
- Managed via the **Profiles** API tag ([[concepts/design-first-openapi]]): create / list / update.

## Selection & override
A verification request chooses a profile via `metadata.profileId` and may layer `metadata.profileOverrides` on top; the response echoes `profileUsed` and `overridesApplied`.

## Related
- [[concepts/validation-profiles]] · [[concepts/signature-validation]] · [[concepts/baseline-profiles]]
- [[entities/validation_job]] (FK `profile_id`) · [[entities/dss]]
