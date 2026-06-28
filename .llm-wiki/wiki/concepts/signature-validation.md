---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
---

# Signature validation

The ETSI EN 319 102-1 process by which a signature's validity is determined, as implemented by [[entities/dss]] and surfaced by [[entities/sign-verify-2]]. It has two halves: cryptographic verification of the signature value, and validity of the signing certificate (certification path + revocation).

## Indications & sub-indications (ETSI EN 319 102-1)
- **TOTAL-PASSED** — signature valid (a soft `FORMAT_FAILURE` still passes).
- **TOTAL-FAILED** — `HASH_FAILURE`, `SIG_CRYPTO_FAILURE`, `REVOKED`, `EXPIRED`, `NOT_YET_VALID`, `SIG_CONSTRAINTS_FAILURE`, `CHAIN_CONSTRAINTS_FAILURE`, …
- **INDETERMINATE** — `NO_POE`, `REVOKED_NO_POE`, `OUT_OF_BOUNDS_NO_POE`, `CRYPTO_CONSTRAINTS_FAILURE_NO_POE`, `TRY_LATER`, `NO_CERTIFICATE_CHAIN_FOUND`, …

## Qualification at two times
Per [[entities/eidas-regulation]] Article 32, certificate qualification is computed at **issuance time** and at **signing/validation time** (ETSI TS 119 615), since a TSP's granted status can change.

## Reports
The four DSS report views feed [[entities/sign-verify-2]] responses: `DiagnosticData` (inputs), `DetailedReport` (process), `SimpleReport` (summary), ETSI TS 119 102-2 Validation Report. The API returns the requested subset via the `reports[]` metadata field ([[concepts/validation-profiles]]).

## Related
- [[entities/dss]] · [[entities/eidas-regulation]] · [[concepts/ades-signature-formats]]
- [[concepts/validation-profiles]] · [[concepts/baseline-profiles]]
