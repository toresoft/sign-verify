---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-003
volatility: warm
---

# DSS Reports

The **four report views** produced by `SignedDocumentValidator.validateDocument()` ([[entities/dss]]) and surfaced by [[entities/sign-verify-2]]'s verification API. The client selects which subset to receive via the `reports[]` metadata field ([[concepts/validation-profiles]]). Allowed values: `simple`, `detailed`, `diagnostic`, `etsi`.

| Report | Type | Content |
|--------|------|---------|
| **DiagnosticData** | inputs | all used + static data fed into validation (certs, revocation, timestamps) |
| **DetailedReport** | process | ETSI EN 319 102-1 validation process output (per-constraint) |
| **SimpleReport** | summary | human-friendly summary of the DetailedReport |
| **ETSI Validation Report** | standard | JAXB form of ETSI TS 119 102-2 Validation Report (`getEtsiValidationReportJaxb()`) |

The API response also carries top-level `indication`/`subIndication` ([[concepts/signature-validation]]) and `signatureFormat`.

## Related
- [[entities/dss]] · [[entities/signeddocumentvalidator]]
- [[concepts/signature-validation]] · [[concepts/validation-profiles]]
