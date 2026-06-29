---
type: concept
created: 2026-06-28
updated: 2026-06-28
sources:
  - sources/siva-research
  - sources/dss-timestamp-api-research
confidence: high
volatility: warm
---

# Signature qualification (eIDAS level)

The **qualification level** answers "is this a QES, an AdES/QC, or a plain AdES?" under eIDAS — orthogonal to the ETSI EN 319 102-1 `indication`/`subIndication` (which answer "is the signature cryptographically valid?"). [[entities/siva|SiVa]] exposes it as `signatureLevel`; [[entities/sign-verify-2]] does **not yet** surface it. See [[analyses/siva-vs-sign-verify-2]] (improvement #2).

## DSS 6.4 API (verified via javap)

`eu.europa.esig.dss.simplereport.SimpleReport`:
- `SignatureQualification getSignatureQualification(String signatureId)`
- `TimestampQualification getTimestampQualification(String timestampId)`
- `List<Message> getQualificationErrors(String)` / `getQualificationWarnings(String)` / `getQualificationInfo(String)` — the reasons behind the level.

DSS computes this per ETSI EN 319 172 / TS 119 615; no extra call needed beyond the existing `validateDocument` → `SimpleReport`.

## `SignatureQualification` — full value set (DSS 6.4, `dss-enumerations`)

23 values. `.name()` is already the ETSI-standard label; `.getReadable()` gives a human string.

| Group | Values |
|---|---|
| Qualified | `QESIG`, `QESEAL` |
| AdES/QC | `ADESIG_QC`, `ADESEAL_QC` |
| AdES | `ADESIG`, `ADESEAL` |
| Unknown (positive-ish) | `UNKNOWN_QC_QSCD`, `UNKNOWN_QC`, `UNKNOWN` |
| Not AdES | `NOT_ADES_QC_QSCD`, `NOT_ADES_QC`, `NOT_ADES` |
| Indeterminate (one per positive value) | `INDETERMINATE_QESIG`, `INDETERMINATE_QESEAL`, `INDETERMINATE_UNKNOWN_QC_QSCD`, `INDETERMINATE_ADESIG_QC`, `INDETERMINATE_ADESEAL_QC`, `INDETERMINATE_UNKNOWN_QC`, `INDETERMINATE_ADESIG`, `INDETERMINATE_ADESEAL`, `INDETERMINATE_UNKNOWN` |
| None | `NA` |

`TimestampQualification`: `QTSA`, `TSA`, `NA` (SiVa's `timestampLevel`).

## DSS → SiVa `signatureLevel` mapping

SiVa's enum is essentially a subset/rename of DSS's. The DSS set is the **superset** (adds `UNKNOWN_*`, splits `NOT_ADES_QC`/`NOT_ADES`, and has explicit `INDETERMINATE_*`).

| SiVa `signatureLevel` | DSS `SignatureQualification` |
|---|---|
| `QESIG` / `QESEAL` | `QESIG` / `QESEAL` |
| `ADESIG_QC` / `ADESEAL_QC` | `ADESIG_QC` / `ADESEAL_QC` |
| `ADES_QC` (generic) | `UNKNOWN_QC` (sig/seal unknown) |
| `ADESIG` / `ADESEAL` | `ADESIG` / `ADESEAL` |
| `ADES` (generic) | `UNKNOWN` |
| `NOT_ADES_QC_QSCD` | `NOT_ADES_QC_QSCD` |
| `NA` | `NA` / `NOT_ADES` / `NOT_ADES_QC` |
| `INDETERMINATE_*` | `INDETERMINATE_*` |

## Recommendation for sign-verify-2

**Do not invent a new enum.** Expose DSS `SignatureQualification.name()` directly as `signatureLevel` (it is the ETSI-standard label, a superset of SiVa's). Optionally add `signatureLevelLabel` from `.getReadable()` and surface `getQualificationWarnings()` in `warnings[]`.

- Add `signatureLevel` (+ per-timestamp `timestampLevel`) to the enriched report DTO — bundle with [[analyses/siva-vs-sign-verify-2]] improvement #1 and the TSD [[analyses/tsd-dto-mapping]] Phase 4.
- In `DssValidatorAdapter`/assembler: `simple.getSignatureQualification(sigId)` for the reporting signature; map null → `NA`.

## Related
- [[concepts/etsi-en-319-102-1-validation]] · [[concepts/reports]] · [[analyses/tsd-dto-mapping]]
- [[analyses/siva-vs-sign-verify-2]] · [[entities/siva]] · [[entities/dss]]
- [[concepts/firma-con-spid]] — firme SPID si presentano come `QESEAL` (sigillo del gestore)
