---
type: concept
category: api-reference
created: 2026-06-28
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-28-001
  - sources/siva-research
tags: [siva, rest, api, interface, validation, eidas]
confidence: high
summary: "SiVa 3.x REST/JSON contract — /validate, /validateHashcode, /getDataFiles, monitoring endpoints; request/response schema, report tiers, error shape. SOAP removed in 3.10."
volatility: warm
---

# SiVa REST interface (3.x)

SiVa exposes a **REST/JSON** API (X-Road v6 compatible, optional). **SOAP was removed in 3.10** — ` /soap/validationWebService/validateDocument`, `/soap/hashcodeValidationWebService`, `/soap/dataFilesWebService/getDocumentDataFiles` and their `?wsdl` are gone. All facts below are grounded in [[sources/SRC-2026-06-28-001]] (`docs/siva3/interfaces.md`).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/validate` | Validate signatures in a signed document/container |
| POST | `/validateHashcode` | Validate XAdES without datafiles (hashcode mode) |
| POST | `/getDataFiles` | Extract data files from a DDOC container |
| GET | `/monitoring/health` (+ `.json`) | Spring Boot Actuator health (+ SiVa `components.health`) |
| GET | `/monitoring/heartbeat` | Simplified `{status}` |
| GET | `/monitoring/version` | `{version}` |
| GET | `/monitoring/prometheus` | Prometheus exposition format |

`/monitoring/health` adds a SiVa-specific indicator `components.health` with `webappName`, `version`, `buildTime`, `startTime`, `currentTime` (read from `MANIFEST.MF`).

## `/validate` request

| Param | Mandatory | Type | Notes |
|---|---|---|---|
| `document` | + | String | Base64 of the signed document |
| `filename` | + | String | Max 255 chars |
| `signaturePolicy` | – | String | `POLv3` (all legal levels) · `POLv4` (default; qualified-only) — see [[concepts/siva-validation-policy]] |
| `reportType` | – | String | `Simple` (default) · `Detailed` · `Diagnostic` — see [[concepts/siva-report-schema]] |

## `/validateHashcode` request

Two modes: (1) integrator-side datafile hash match (signature only), (2) SiVa-side match (pass `datafiles[]`). Body is `signatureFiles[]` with `signature` (Base64) and optional `datafiles[]` (`filename`, `hashAlgo`, `hash` Base64). Same optional `signaturePolicy`/`reportType`. **Note:** `Detailed`/`Diagnostic` extra blocks (`validationProcess`, `diagnosticData`) are **not** supported for hashcode.

## `/getDataFiles` request / response

| Req param | Mandatory | Type |
|---|---|---|
| `document` | + | Base64 DDOC |
| `filename` | + | String (DDOC only) |

Response: `dataFiles[]` of `{ fileName, size, base64, mimeType }` — extracted as-is by JDigiDoc, no extra validation.

## Response envelope

```json
{ "validationReport": {
    "validationConclusion": { /* always present */ },
    "validationProcess":   { /* Detailed only, DSS-based only */ },
    "diagnosticData":      { /* Diagnostic only, DSS-based, non-hashcode */ }
  },
  "validationReportSignature": "<base64 ASiC-E, only if report signing enabled + Detailed>" }
```

Full field-by-field schema: [[concepts/siva-report-schema]].

## Error shape (HTTP 400)

When no validation report is produced:

```json
{ "requestErrors": [{ "message": "Document malformed or not matching documentType", "key": "document" }] }
```

## Recent protocol deltas

- **3.10** (non-breaking): ASiC-S `timeStampTokens[]` gained `certificates[]`, `subIndication`, `warning[]`, `timestampScopes`, `timestampLevel`; ASiC-S timestamp validation logic delegated to DSS; POE considered for nested timestamped ASiC-S; custom constraint files supported.
- **3.4**: `/monitoring/heartbeat`, `/monitoring/version` added.
- **3.3**: `info.timestampCreationTime`, `info.ocspResponseCreationTime`, `info.signingReason` (PAdES).
- **3.2**: `signatureMethod`, `info.timeAssertionMessageImprint`, `info.signatureProductionPlace`, `info.signerRole`, `certificates[]`.
- **v2→v3**: `reportType=Diagnostic` added; `/validateHashcode` accepts multiple signatures; `validatedDocument` optional; `fileHash` now Base64 (was hex); `subjectDistinguishedName` fields.

## Relation to sign-verify-2

[[entities/sign-verify-2]] is **design-first OpenAPI** ([[concepts/design-first-openapi]]) with sync **and** async jobs ([[concepts/async-verification-jobs]]) plus HMAC callbacks ([[concepts/hmac-callback])). SiVa is **sync-only**, no async/batch/callback, and contract-first (docs-derived, no published OpenAPI). SiVa's value-adds absent in plain DSS wrappers: hashcode mode, DDOC datafile extraction, and the **signed ASiC-E validation report** for verdict non-repudiation.

## See also

[[entities/siva]] · [[concepts/siva-validation-policy]] · [[concepts/siva-report-schema]] · [[concepts/siva-deployment-ops]] · [[analyses/siva-vs-sign-verify-2]] · [[concepts/etsi-en-319-102-1-validation]]
