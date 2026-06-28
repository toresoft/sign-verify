---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
---

# DetachedTimestampValidator

> ⚠️ **Scope correction.** This validator is only for a **naked/detached RFC 3161 timestamp token** (e.g. a raw `.tsr`, or a CMS `TimeStampToken` with *no* signed inner content). The common Italian-PA **`.tsd` case is *not* this** — a `.tsd` there is a CAdES envelope carrying **entity signature(s) + embedded timestamp(s)**, handled directly by [[entities/signeddocumentvalidator|SignedDocumentValidator.fromDocument()]] (CAdES autodetect, walks all nested signatures/timestamps). See [[analyses/verifica-file-tsd]].

The [[entities/dss]] validator for **standalone / detached RFC 3161 timestamp tokens** (timestamp-only, no signed content). It **extends `SignedDocumentValidator`** and implements `TimestampValidator`, keeping the same contract (`.setCertificateVerifier(cv)` + `.validateDocument()` → [[concepts/reports|Reports]] with `indication`/`subIndication`) — the "signature" validated is the **TSA's signature over the timestamp**, not an entity signature over document content.

## API (package `eu.europa.esig.dss.validation.timestamp`)
- `DetachedTimestampValidator(DSSDocument timestampFile)`
- `DetachedTimestampValidator(DSSDocument timestampFile, TimestampType timestampType)`
- `DetachedTimestampValidator(DetachedTimestampAnalyzer analyzer)`
- `getTimestamp()` → the `TimestampToken` being validated.
- `getDocumentAnalyzer()` → the timestamped document(s) *without* the signature.

## Detection
`DetachedTimestampAnalyzer` / `DetachedTimestampProcessor` decide whether a CMS/DER blob is a **timestamp-only** document. This is the branch condition [[entities/dss-validator-adapter|DssValidatorAdapter]] needs: timestamp-only → this validator; otherwise → `SignedDocumentValidator.fromDocument(...)`.

## Why not `SignedDocumentValidator.fromDocument()` alone
`fromDocument()` auto-detects XAdES/CAdES/PAdES/JAdES/ASiC **entity signatures**; a pure timestamp token (`.tsd`/`.tsr` data container) is not an AdES signature and must be routed explicitly (see [[analyses/verifica-file-tsd|the .tsd analysis]]). Note: a `.tsd` could *also* be a CAdES envelope carrying a real signature + an archive timestamp — the analyzer distinguishes by content, not extension (DSS detects by content).

## Related
- [[concepts/timestamping]] · [[entities/dss]] · [[entities/signeddocumentvalidator]]
- [[concepts/signature-validation]] · [[concepts/reports]] · [[concepts/trusted-lists]]