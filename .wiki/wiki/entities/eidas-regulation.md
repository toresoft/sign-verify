---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
  - articles/2026-06-28-eidas-regolamento-910-2014-approfondimento
volatility: warm
---

# eIDAS Regulation

**EU Regulation 910/2014** — the legal framework for electronic identification, trust services and electronic signatures in the European Union. It defines *who* may use electronic signatures/seals and in what context, and mandates cross-border interoperability via baseline profiles ([[concepts/baseline-profiles]]).

## Key articles for electronic signatures

### Art. 25 — Legal effects
- An electronic signature shall not be denied legal effect/admissibility solely because it is electronic or not qualified.
- A **qualified electronic signature (QES)** has the equivalent legal effect of a handwritten signature.
- A QES based on a qualified certificate issued in one EU member state shall be **recognised as QES in all other member states** (mandatory mutual recognition, no further formalities required).

### Art. 26 — Requirements for Advanced Electronic Signature (AES)
Must be: (a) uniquely linked to the signatory; (b) capable of identifying the signatory; (c) created using signature-creation data under the signatory's sole control; (d) linked to the signed data so subsequent changes are detectable.

### Art. 32 — QES validation requirements
The validation process must confirm 8 conditions: qualified certificate valid at signing time, issued by QTSP, correct signature validation data, signatory identity correctly provided, pseudonym indication if used, SSCD used, data integrity preserved, Art. 26 requirements met at signing time.

### Art. 42 — Qualified electronic time stamps
Must: (a) bind date/time to data to preclude undetectable changes; (b) use an accurate UTC-linked time source; (c) be signed with an advanced electronic signature/seal of the QTSP. Art. 41 grants them **presumption of accuracy** of the indicated date/time and data integrity.

### Art. 22 — Trusted Lists
Each member state must publish and maintain a national Trusted List of QTSPs and their qualified trust services. The Commission publishes the LOTL aggregating all national TLs.

## Commission Implementing Decisions
- **2015/1505** — trusted list formats (ETSI TS 119 612)
- **2015/1506** — baseline signature format specifications
- **2025/2164** — TLv6 migration (applicable from 29 April 2026)
- **2025/1929** — binding date/time and accuracy of time sources for qualified time stamps

## eIDAS 2.0 — Regulation (EU) 2024/1183
Key changes to the signature framework:
- Introduces **mandatory termination plans** for QTSPs (anticipated and unanticipated termination, e.g. bankruptcy)
- Termination plan reviewed at least every 2 years
- European Digital Identity Wallet (EUDI Wallet)
- Extended trust services: electronic archiving, electronic attestation of attributes, electronic ledgers

## How it shapes the codebase
[[entities/sign-verify-2]] exists to give eIDAS-compliant verification results; [[entities/dss]] implements the ETSI EN 319 102-1 validation process and the qualification logic of ETSI TS 119 615 that eIDAS Article 32 requires.

## Related
- [[entities/dss]] · [[concepts/trusted-lists]] · [[concepts/baseline-profiles]]
- [[concepts/signature-validation]] (ETSI EN 319 102-1 process)
- [[concepts/timestamping]] · [[concepts/etsi-ades-formats]] · [[concepts/x509-pki-profiles]]
- [[concepts/italian-digital-signature-law]] — recepimento italiano (CAD, DPCM 2013, AgID, CNIPA 45/2009)
