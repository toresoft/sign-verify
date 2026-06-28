---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-003
---

# AdES signature formats

The five **Advanced Electronic Signature** container/encoding formats supported by [[entities/dss]] and verified by [[entities/sign-verify-2]]. Each targets a different carrier; all implement the [[concepts/baseline-profiles]] B/T/LT/LTA levels.

| Format | Carrier | Typical use |
|--------|---------|-------------|
| **XAdES** | XML | XML document signing (XMLDSig + XAdES attributes) |
| **CAdES** | CMS (binary) | Generic binary / detached signatures |
| **PAdES** | PDF | PDF document signing |
| **JAdES** | JSON (JWS) | REST/API/JSON payloads |
| **ASiC** | ZIP container | Associated Signature Containers (XAdES or CAdES inside) |

## In the codebase
- `SignedDocumentValidator.fromDocument()` auto-selects the right validator from the classpath ([[entities/dss]]).
- The verification response surfaces the detected `signatureFormat` (e.g. `PAdES-BASELINE-B`).
- [[concepts/file-extraction]]: ASiC-E typically yields **multiple** originals (→ ZIP), others yield a single original.

## Profiles
Baseline profiles are codified in ETSI TS 103 171 (XAdES), 103 173 (CAdES), 103 172 (PAdES), 103 174 (ASiC); JAdES in TS 119 182. See [[concepts/baseline-profiles]].

## Related
- [[concepts/baseline-profiles]] · [[entities/dss]] · [[concepts/signature-validation]]
- [[entities/eidas-regulation]] · [[concepts/file-extraction]]
