---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
volatility: warm
---

# Baseline profiles

The eIDAS-mandated signature level hierarchy (Commission Implementing Decision 2015/1506). Each level is an **augmentation** that adds properties on top of the previous one, progressively strengthening long-term verifiability. Applies to all [[concepts/ades-signature-formats]].

```
B  ──►  T  ──►  LT  ──►  LTA
```

| Level | Adds | DSS elements |
|-------|------|--------------|
| **B** (BASELINE-B) | Signed attributes set at creation (immutable) | signing-time, signing-certificate, signer-role, commitment-type, etc. |
| **T** (BASELINE-T) | Trusted timestamp over the signature | `UnsignedSignatureProperties` / timestamp |
| **LT** (BASELINE-LT) | Validation data (certs + revocation) embedded | `CertificateValues`, `RevocationValues` |
| **LTA** (BASELINE-LTA) | Archive timestamps + missing validation data | `ArchiveTimeStamp`, `TimeStampValidationData`, `AnyValidationData` |

## Why levels matter
Higher levels preserve verifiability *after* certificates expire or algorithms weaken — LT/LTA carry the revocation evidence and archive timestamps needed to re-establish validity at a future date.

## Encapsulation strategies
DSS lets you control where validation data is placed per level via `ValidationDataEncapsulationStrategy` (e.g. `CERTIFICATE_REVOCATION_VALUES_AND_TIMESTAMP_VALIDATION_DATA_AND_ANY_VALIDATION_DATA`). The default and a legacy (pre-6.1) variant differ in where LTA data lands.

## In the codebase
[[concepts/validation-profiles]] in [[entities/sign-verify-2]] wrap DSS validation *policies*; the DSS **policy XML** `Level` attribute selects which constraints apply ([[sources/SRC-2026-06-27-003]] §4.2).

## Related
- [[concepts/ades-signature-formats]] · [[entities/dss]] · [[concepts/validation-profiles]]
- [[entities/eidas-regulation]]
