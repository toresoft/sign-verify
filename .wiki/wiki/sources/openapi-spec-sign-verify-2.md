---
type: source
title: "sign-verify-2 OpenAPI contract (openapi.yaml v1.0.0)"
slug: openapi-spec-sign-verify-2
status: ingested
created: 2026-06-29
updated: 2026-06-29
category: api-contract
confidence: high
verified: 2026-06-29
volatility: warm
sources:
  - notes/2026-06-28-openapi-spec-sign-verify-2
---

# sign-verify-2 — OpenAPI contract reference

**Spec**: OpenAPI 3.0.3, `src/main/resources/openapi/openapi.yaml`, v1.0.0.
**Auth**: `X-API-Key` header (ApiKeyAuth) or `Authorization: Bearer <JWT>` (OAuth2Bearer).
Roles: `PRIVILEGED` (admin) / `STANDARD` (default).

## Endpoint groups

| Tag | Base path | Notes |
|---|---|---|
| Verifications | `/api/v1/verifications` | POST sync → `VerificationResponse`; POST `/async` → job; GET `/jobs/{jobId}` |
| Extractions | `/api/v1/extractions` | POST → binary or ZIP; headers `X-Signature-Format`, `X-Document-Count` |
| Profiles | `/api/v1/profiles` | CRUD + `/default`; presets BASIC/STANDARD/STRICT/CUSTOM |
| ApiKeys | `/api/v1/api-keys` | PRIVILEGED; list/create/delete/patch(enable) |
| Tsl | `/api/v1/tsl/status|refresh|certificates` | status public read; refresh PRIVILEGED; certs filterable |
| Audit | `/api/v1/audit-log` | PRIVILEGED; filters: principal, action, targetType, from/to, success |
| Health | `/actuator/health` | Public; component detail PRIVILEGED only |

## Key schemas — VerificationResponse

```
verifiedAt          date-time
profileUsed         string
overridesApplied    boolean
signatureFormat     string        (overall format)
indication          string        TOTAL_PASSED / TOTAL_FAILED / INDETERMINATE (ETSI EN 319 102-1)
subIndication       string?
signatureCount      integer
signatures[]        SignatureSummary[]
timestamps[]        TimestampSummary[]
reports             object        (simple/detailed/diagnostic/etsi requested via metadata.reports[])
```

## SignatureSummary — firma vs sigillo

```
id                  string
indication          string        (per-signature ETSI outcome)
subIndication       string?
signatureFormat     string?
signatureLevel      string        ← DSS SignatureQualification enum
signedBy            string?
bestSignatureTime   date-time?
claimedSigningTime  date-time?
archiveTimestamps[] TimestampSummary[]
certificates[]      CertificateSummary[]    [{id, qualifiedName, sunsetDate?}]
timestamps[]        TimestampSummary[]
```

**`signatureLevel`** distinguishes:

| Pattern in value | Meaning |
|---|---|
| `SIG` (no SEAL) | Firma elettronica (persona fisica) |
| `SEAL` | Sigillo elettronico (persona giuridica) |
| prefix `Q` | Qualificato (es. `QESIG`, `QESEAL`) |
| prefix `ADES` + `_QC` | AdES su certificato qualificato |
| `UNKNOWN_*`, `NA`, `NOT_ADES*` | Non classificabile |
| `INDETERMINATE_*` | Classificazione indeterminata |

`indication` (validità crittografica) e `signatureLevel` (qualifica eIDAS) sono **ortogonali**.

## TimestampSummary

```
id              string
indication      string?
subIndication   string?
productionTime  date-time?
qualification   string      DSS TimestampQualification: QTSA / TSA / NA
```

## Profile presets

`BASIC`/`STANDARD`: accettano AdES + qualificato (type-agnostic, ≈SiVa POLv3).
`STRICT`: solo QES/QESeal (≈SiVa POLv4).
`CUSTOM`: richiede `policyXml`.

## Error handling

Tutti gli errori: `application/problem+json` (`Problem` schema — RFC 9457, fields: type, title, status, detail, instance, traceId).
Codes: 400 / 401 / 403 / 404 / 409 / 410 Gone / 429 TooManyRequests.

## Job lifecycle

```
PENDING → RUNNING → COMPLETED → DELIVERED
                  ↘ FAILED
                             → DELIVERY_FAILED
DELETED (any terminal state)
```

## See Also

- [[entities/sign-verify-2]] — service overview, porte esagonali
- [[concepts/signature-qualification]] — `signatureLevel` enum, DSS `getSignatureQualification()`
- [[concepts/reports]] — quattro tipi di report DSS
- [[concepts/validation-profiles]] — preset policy, `reports[]` metadata
- [[concepts/api-key-authentication]] — X-API-Key auth details
- [[concepts/async-verification-jobs]] — job lifecycle, callback HMAC
