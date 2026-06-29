---
title: "sign-verify-2 OpenAPI spec (openapi.yaml)"
source: "src/main/resources/openapi/openapi.yaml"
type: notes
ingested: 2026-06-29
tags: [openapi, api-contract, sign-verify-2, verification, signature, seal, signatureLevel, rest]
summary: "OpenAPI 3.0.3 contract for sign-verify-2. Defines all REST endpoints (Verifications, Extractions, ApiKeys, Profiles, TSL, Audit, Health), request/response schemas, and authentication schemes. Key schema: SignatureSummary.signatureLevel (DSS SignatureQualification enum) distinguishes firma (SIG) from sigillo (SEAL) per-signature. Auth: X-API-Key header or OAuth2 JWT Bearer."
---

# sign-verify-2 OpenAPI spec

**File**: `src/main/resources/openapi/openapi.yaml`  
**Spec version**: OpenAPI 3.0.3  
**API version**: 1.0.0  
**Auth**: `X-API-Key` header (ApiKeyAuth) or `Authorization: Bearer <JWT>` (OAuth2Bearer).  
Roles: `STANDARD` (default) and `PRIVILEGED` (admin operations).

---

## Endpoints summary

### Verifications

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/verifications` | STANDARD | Sync signature verification → `VerificationResponse` |
| POST | `/api/v1/verifications/async` | STANDARD | Queue async job → `JobAcceptedResponse` (202) |
| GET | `/api/v1/verifications/jobs/{jobId}` | STANDARD/PRIVILEGED | Poll job status+result → `JobView` |

Request: `multipart/form-data` with `file` (binary) + optional `metadata` JSON (`profileId?`, `profileOverrides?`, `reports[]?`).

### Extractions

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/extractions` | Extract original content from signed file/ASiC → binary or ZIP |

Response headers: `X-Signature-Format`, `X-Document-Count`.

### ApiKeys (PRIVILEGED only)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/api-keys` | List (paginated) |
| POST | `/api/v1/api-keys` | Create (returns plaintext key once) |
| DELETE | `/api/v1/api-keys/{id}` | Delete (last PRIVILEGED key protected) |
| PATCH | `/api/v1/api-keys/{id}` | Enable/disable |

### Profiles

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/profiles` | List |
| POST | `/api/v1/profiles` | Create (preset: BASIC/STANDARD/STRICT/CUSTOM) |
| GET | `/api/v1/profiles/{id}` | Get |
| PUT | `/api/v1/profiles/{id}` | Update description/policyXml |
| DELETE | `/api/v1/profiles/{id}` | Delete (default profile protected) |
| POST | `/api/v1/profiles/{id}/default` | Set as default |

Preset semantics: `BASIC`/`STANDARD` accept AdES + qualified (type-agnostic, ≈SiVa POLv3). `STRICT` enforces QES/QESeal only (≈SiVa POLv4). `CUSTOM` requires `policyXml`.

### TSL

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/tsl/status` | EU Trusted List mirror status |
| POST | `/api/v1/tsl/refresh` | Force async refresh (PRIVILEGED) |
| GET | `/api/v1/tsl/certificates` | List trusted certs (filterable: ski, aki, subjectCn, country, tspName, tspServiceType, tspServiceStatus, validAt, …) |
| GET | `/api/v1/tsl/certificates/{id}` | Get single cert |

### Audit (PRIVILEGED)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/audit-log` | Paginated, filterable (principalId, action, targetType, from/to, success) |

### Health (public)

| Method | Path | Description |
|---|---|---|
| GET | `/actuator/health` | Aggregate health; component details visible to PRIVILEGED only |

Health components include: `tslReadiness` (certificateCount, lastRefresh), `jobQueue` (activeJobs), `dss`, `db`.

---

## Key schemas

### VerificationResponse

```
verifiedAt         date-time
profileUsed        string
overridesApplied   boolean
signatureFormat    string
indication         string   — ETSI EN 319 102-1: TOTAL_PASSED / TOTAL_FAILED / INDETERMINATE
subIndication      string?
signatureCount     integer
signatures[]       SignatureSummary[]
timestamps[]       TimestampSummary[]
reports            object (map of requested report types)
```

### SignatureSummary — FIRMA vs SIGILLO

```
id                  string
indication          string
subIndication       string?
signatureFormat     string?
signatureLevel      string   ← DSS SignatureQualification enum (see below)
signedBy            string?
bestSignatureTime   date-time?
claimedSigningTime  date-time?
archiveTimestamps[] TimestampSummary[]
certificates[]      CertificateSummary[]
timestamps[]        TimestampSummary[]
```

**`signatureLevel` values** (DSS `SignatureQualification`):

| Value contains | Meaning |
|---|---|
| `SIG` (no `SEAL`) | Firma elettronica — persona fisica |
| `SEAL` | Sigillo elettronico — persona giuridica |
| `UNKNOWN`, `NA`, `NOT_ADES*` | Non classificabile |
| `INDETERMINATE_*` | Classificazione indeterminata |

Prefix `Q` = qualificato (QES/QESeal), `ADES` = AdES, `_QC` = su certificato qualificato.

Examples: `QESIG`, `QESEAL`, `ADESIG_QC`, `ADESEAL_QC`, `ADESIG`, `ADESEAL`, `INDETERMINATE_QESIG`, `UNKNOWN_QC`, `NA`, `NOT_ADES`.

**Nota**: `indication` (TOTAL_PASSED/TOTAL_FAILED/INDETERMINATE) risponde a "la firma è valida?". `signatureLevel` risponde a "è una firma o un sigillo, e a che livello eIDAS?". Sono ortogonali.

### TimestampSummary

```
id              string
indication      string?
subIndication   string?
productionTime  date-time?
qualification   string   — DSS TimestampQualification: QTSA (qualified TSA), TSA, or NA
```

### Other schemas

- `ApiKeyView`: id, name, keyPrefix, role (PRIVILEGED/STANDARD), enabled, bootstrap, createdAt, expiresAt?, lastUsedAt?
- `ApiKeyCreatedResponse`: extends `ApiKeyView` + `plaintextKey` (one-time)
- `ProfileView`: id, name, description?, preset, policyXml?, isDefault, createdAt, updatedAt
- `JobView`: jobId, status (PENDING/RUNNING/COMPLETED/FAILED/DELIVERED/DELIVERY_FAILED/DELETED), createdAt, startedAt?, completedAt?, deliveredAt?, expiresAt?, callbackAttempts, result?
- `CertificateSummary`: id, qualifiedName, sunsetDate?
- `Problem`: RFC 9457 — type, title, status, detail, instance, traceId
- `Page`: page, size, totalElements, totalPages
- `HealthStatus`: status (UP/DOWN/OUT_OF_SERVICE/UNKNOWN), groups[], components{}

### Error responses

All errors return `application/problem+json` with `Problem` schema:
`400 BadRequest` · `401 Unauthorized` · `403 Forbidden` · `404 NotFound` · `409 Conflict` · `410 Gone` · `429 TooManyRequests`

---

## Related wiki entries

- [[concepts/signature-qualification]] — `signatureLevel` enum, DSS API
- [[concepts/reports]] — four report types (simple/detailed/diagnostic/etsi)
- [[concepts/api-key-authentication]] — authentication details
- [[concepts/validation-profiles]] — profile presets, `reports[]` metadata field
- [[entities/sign-verify-2]] — service overview
- [[analyses/siva-vs-sign-verify-2]] — gap analysis vs SiVa
