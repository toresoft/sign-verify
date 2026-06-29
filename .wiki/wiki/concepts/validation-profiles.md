---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-29
verified: 2026-06-29
volatility: warm
confidence: high
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
  - sources/openapi-spec-sign-verify-2
---

# Validation profiles

The sign-verify-2 abstraction over a **DSS validation policy** (policy XML). A profile bundles the constraints applied during [[concepts/signature-validation]] and is stored in the `verification_profile` table.

## Three layers of customization
1. **Presets (built-in)** — ship a canonical policy XML loaded from `resources/policy/<PRESET>.xml` by `PresetXmlLoader`.
2. **Custom profiles** — admin-defined policies persisted in the DB (`is_default` flag marks the fallback).
3. **Per-request overrides** — `profileOverrides` JSON on a single verification call, applied on top of the selected profile (`overridesApplied: true` in the response).

## Built-in presets

`ProfilePreset` enum (OpenAPI `preset` field, [[sources/openapi-spec-sign-verify-2]]):

| Preset | Semantics |
|---|---|
| `BASIC` / `STANDARD` | Accept AdES and qualified signatures, type-agnostic (cf. SiVa POLv3). `STANDARD` is the seeded **system default**. |
| `STRICT` | Qualified-only semantics (QES/QESeal, cf. SiVa POLv4). |
| `AGID` | **Italian QES / firma digitale** — qualified-only: `QcCompliance=FAIL` + `QcSSCD=FAIL` (QES on a QSCD, eIDAS art. 3(12)/CAD art. 24) + `KeyUsage nonRepudiation=FAIL` (DPCM art. 28) + `TrustServiceTypeIdentifier=CA/QC` and `TrustServiceStatus` granted (`FAIL`) + `QcLegislationCountryCodes=IT` (`WARN`, mutual recognition). See [[concepts/italian-digital-signature-law]]. |
| `AGID_TS` | As `AGID`, plus a mandatory valid timestamp via `<TLevelTimeStamp Level="FAIL"/>` (T-level POE required). |
| `CUSTOM` | Requires an explicit `policyXml`. |

> ⚠️ Until 2026-06-29 the `BASIC`/`STANDARD`/`STRICT` policy XMLs were **byte-identical placeholders** ("QES AES/QC AES TL based") — the preset distinction was not yet real. `AGID`/`AGID_TS` are the first presets with genuinely differentiated constraints (derived from `STANDARD.xml`, validated against `dss-policy-jaxb` `policy.xsd`).

### Preloading
`ProfileSeeder` (on `ApplicationReadyEvent`) seeds the `STANDARD` system default **and** two ready-to-use non-default profiles named `agid` and `agid-ts`, idempotently by name (skipped if already present, so an operator can customise or delete them). Neither becomes the default.

## The `Level` attribute (key concept)
The DSS policy XML's `Level` attribute drives which constraint groups are evaluated (basic / long-term / archival). See [[sources/SRC-2026-06-27-003]] §4.2 "Policy XML format". This is the central knob mapping to [[concepts/baseline-profiles]].

## Lifecycle
- Created/listed/updated via the **Profiles** API tag ([[sources/SRC-2026-06-27-008]]).
- Selected per verification via `metadata.profileId`; omitted → default profile.
- The verification response echoes `profileUsed` so callers can audit what was applied.

## Related
- [[entities/dss]] · [[concepts/signature-validation]] · [[concepts/baseline-profiles]]
- [[entities/sign-verify-2]] · [[concepts/design-first-openapi]]
- [[concepts/italian-digital-signature-law]] (AGID/AGID_TS legal basis) · [[concepts/signature-qualification]] · [[concepts/dss-policy-xml]]
- [[concepts/firma-con-spid]] — le firme SPID sono QSeal, fuori dal perimetro nonRepudiation di AGID
