---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-003
  - sources/SRC-2026-06-27-001
---

# DSS policy XML

The XML validation policy format consumed by [[entities/dss]], wrapped by [[concepts/validation-profiles|Validation profiles]] in [[entities/sign-verify-2]]. Defines the constraints applied during [[concepts/signature-validation]].

## The key concept: the `Level` attribute
The policy XML's `Level` attribute is the central knob — it selects **which constraint groups** are evaluated, aligned to [[concepts/baseline-profiles]] (basic / long-term / archival). This is how a profile maps to a signature level without redefining every constraint.

## Document structure
A policy contains: model declaration, a set of constraints (each with a `Level`), and basic/long-term/archival constraint blocks. Constraint shapes cover format, cryptographic strength, revocation freshness, certificate-chain, and timestamp ordering.

## In sign-verify-2
- **Presets** ship a sensible default policy; **custom profiles** persist an admin-defined policy in [[entities/verification_profile]]; **per-request overrides** layer `profileOverrides` JSON on top. The response echoes `profileUsed` + `overridesApplied`. See [[sources/SRC-2026-06-27-003]] §4.2.

## Related
- [[concepts/validation-profiles]] · [[concepts/baseline-profiles]]
- [[entities/dss]] · [[entities/verification_profile]] · [[concepts/signature-validation]]
