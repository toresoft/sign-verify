---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
volatility: warm
---

# DSS — Digital Signature Service

Open-source **Java framework** by the European Commission (Digital Building Blocks programme) that implements creation, augmentation and **validation** of electronic signatures under [[entities/eidas-regulation]] across all five [[concepts/ades-signature-formats]] (XAdES, CAdES, PAdES, JAdES, ASiC). Current documented version: **6.4** (2024-07-24).

## Why it matters here
[[entities/sign-verify-2]] wraps DSS behind a domain port (`SignatureValidatorPort`) implemented by `DssValidatorAdapter`, `DssExtractionAdapter` and `DssTslAdapter` ([[concepts/hexagonal-architecture]]). DSS is the engine; the service is the API/auth/audit/persistence shell around it.

## Core validation API
- `SignedDocumentValidator.fromDocument(doc)` → auto-selects the format validator from the classpath.
- `.setCertificateVerifier(cv)` → attach trust/revocation config.
- `.validateDocument()` → returns a `Reports` object with **four views**: `DiagnosticData`, `DetailedReport` (ETSI EN 319 102-1), `SimpleReport`, and `getEtsiValidationReportJaxb()` (ETSI TS 119 102-2).
- Outcomes follow [[concepts/signature-validation]] indications: TOTAL-PASSED / TOTAL-FAILED / INDETERMINATE.

## Trusted Lists
`TLValidationJob` + `LOTLSource`/`TLSource` + `TrustedListsCertificateSource` drive the [[concepts/trusted-lists]] pipeline. EU LOTL at `https://ec.europa.eu/tools/lotl/eu-lotl.xml`, pivot support on, OJ keystore as trust anchor.

## Docs reference
Full 523-page manual captured in [[sources/SRC-2026-06-27-001]] (indexed in KB as `DSS-6.4-documentation`). Pivotal chapters: §3 concepts, §6 revocation, §7 validation, §10 augmentation, §11 trusted lists.

## Related
- [[entities/eidas-regulation]] · [[concepts/ades-signature-formats]] · [[concepts/baseline-profiles]]
- [[entities/sign-verify-2]] · [[concepts/hexagonal-architecture]]
