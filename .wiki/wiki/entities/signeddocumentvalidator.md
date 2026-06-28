---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
---

# SignedDocumentValidator

The central [[entities/dss]] entry point for **signature validation**. `SignedDocumentValidator.fromDocument(doc)` auto-selects the right format-specific validator from the classpath (XAdES/CAdES/PAdES/JAdES/ASiC), then `.setCertificateVerifier(cv)` attaches trust/revocation config and `.validateDocument()` returns a [[concepts/reports|Reports]] object.

In [[entities/sign-verify-2]] this lives behind the `SignatureValidatorPort`, implemented by [[entities/dssvalidatoradapter|DssValidatorAdapter]] ([[concepts/hexagonal-architecture]]).

> Note (DSS docs §7): the signature must cover the entire document for DSS to validate it; XAdES may apply transformations.

## Related
- [[entities/dss]] · [[entities/certificate-verifier]] · [[concepts/reports]]
- [[concepts/signature-validation]] · [[entities/dssvalidatoradapter]]
