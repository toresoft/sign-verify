---
type: concept
category: api-reference
created: 2026-06-28
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-28-001
  - sources/siva-research
tags: [siva, report, simple, detailed, diagnostic, signature-level, qualification, eidas]
confidence: high
summary: "SiVa validation report tiers (Simple/Detailed/Diagnostic), validationConclusion field schema, signatureLevel qualification enum, timeStampTokens, Estonian-context message filtering, and the signed ASiC-E report."
---

# SiVa validation report schema

SiVa returns three report tiers selected via `reportType` ([[concepts/siva-rest-interface]]). The `validationConclusion` block is always present and identical across tiers; higher tiers add blocks. Source: [[sources/SRC-2026-06-28-001]] (`docs/siva3/interfaces.md`).

## Tiers

| `reportType` | Blocks returned | Notes |
|---|---|---|
| `Simple` (default) | `validationConclusion` | Hashcode-compatible |
| `Detailed` | + `validationProcess` + `validationReportSignature?` | DSS-based only; **only Detailed is signed** when report signing is on |
| `Diagnostic` | + `diagnosticData` | DSS-based, **non-hashcode** only |

`validationProcess` is built on the **DSS detailed report**; `diagnosticData` is raw DSS diagnostic data.

## `validationConclusion` shape

```
policy{policyName, policyDescription, policyUrl}
signaturesCount, validSignaturesCount        # valid = signature.indication == TOTAL-PASSED
validationTime                                 # service-side validation time
validationLevel?                               # e.g. ARCHIVAL_DATA (DSS-based only)
validationWarnings[]?                          # do not affect overall result
validatedDocument?{filename, fileHash?, hashAlgo?}   # filename absent for hashcode
signatureForm                                   # DIGIDOC_XML_1.x[_hashcode] | ASiC-E | ASiC-S | ...
signatures[]?
timeStampTokens[]?                              # ASiC-S TST containers
```

## `signatures[i]` key fields

- `indication` / `subIndication` — ETSI EN 319 102-1 Table 5/6: `TOTAL-PASSED` · `TOTAL-FAILED` · `INDETERMINATE` ([[concepts/etsi-en-319-102-1-validation]]).
- `signatureFormat` — baseline profile enum, e.g. `XAdES_BASELINE_LT`, `XAdES_BASELINE_LT_TM`, `PAdES_BASELINE_LTA`, `CAdES_BASELINE_T`, `DIGIDOC_XML_1.3`, `SK_XML_1.0`.
- `signatureLevel` — **legal qualification** per EU 910/2014 (see box below); **absent for DIGIDOC-XML 1.0..1.3** (JDigiDoc doesn't check it).
- `signedBy` — `"surname, givenName, serialNumber"` or CN; plus `subjectDistinguishedName{serialNumber,commonName,givenName,surname}`.
- `claimedSigningTime`, `signatureMethod` (URI), `id`.
- `info.bestSignatureTime` — trusted signing time; `info.timeAssertionMessageImprint` (OCSP nonce for `LT_TM`, timestamp imprint for T/LT/LTA); `info.ocspResponseCreationTime`, `info.timestampCreationTime`; optional `signerRole[].claimedRole`, `signatureProductionPlace{countryName,stateOrProvince,city,postalCode}`, `signingReason` (PAdES), `archiveTimeStamps[]`.
- `signatureScopes[]{name,scope,content,hashAlgo?,hash?}` — what the signature covers (`hashAlgo`/`hash` present for hashcode).
- `certificates[]` — minimal = signer; may include `REVOCATION`, `SIGNATURE_TIMESTAMP`, `ARCHIVE_TIMESTAMP`, `CONTENT_TIMESTAMP`; each `{commonName,type,content( DER X.509 Base64),issuer?}` forming a chain to the trust anchor.
- `warnings[]`, `errors[]` — free-text from the base library.

### `signatureLevel` qualification enum

Positive: `QESIG`, `QESEAL`, `QES`, `ADESIG_QC`, `ADESEAL_QC`, `ADES_QC`, `ADESIG`, `ADESEAL`, `ADES`.
Indeterminate: same names prefixed `INDETERMINATE_` (e.g. `INDETERMINATE_QESIG`).
Negative: above + `NOT_ADES_QC_QSCD`, `NOT_ADES_QC`, `NOT_ADES`, `NA`.

Relates to [[concepts/signature-qualification]].

## `timeStampTokens[]` (ASiC-S)

`{indication(TOTAL-PASSED/TOTAL-FAILED), subIndication?, signedBy, signedTime, certificates[], errors[]?, warnings[]?, timestampScopes[]?, timestampLevel}`. `timestampLevel` ∈ {`QTSA`, `TSA`, `N/A`}. SiVa emits an extra warning when a TST does not cover the container datafile. (`error`/`warning` singular fields are deprecated aliases.)

## Estonian-context message filtering (Simple Report only)

These DSS messages are treated as **false-positive in the Estonian context** and filtered out of the Simple Report (still present in Detailed/Diagnostic):

- Warning: _The organization name is missing in the trusted certificate!_
- Warning: _The trusted certificate does not match the trust service!_
- Error (signatures): _The certificate is not related to a granted status at time-stamp lowest POE time!_ — for ASiC-S TST this error is **moved under warnings** with: _"Found a timestamp token not related to granted status. If not yet covered with a fresh timestamp token, this container might become invalid in the future."_

## Signed validation report (optional)

`siva.report.reportSignatureEnabled=true` (default **off**). Only **Detailed** reports are signed. The `validationReport` object is signed into an **ASiC-E** container (level configurable), Base64-encoded, returned as `validationReportSignature` sibling of `validationReport`. Signing interfaces: **PKCS#11** (HSM/smartcard) or **PKCS#12** (key+cert bundle). Enabling impacts performance. This is SiVa's non-repudiation differentiator vs a plain DSS wrapper.

## See also

[[entities/siva]] · [[concepts/siva-rest-interface]] · [[concepts/siva-validation-policy]] · [[concepts/reports]] · [[concepts/signature-qualification]] · [[concepts/etsi-en-319-102-1-validation]] · [[concepts/timestamping]] · [[concepts/rfc5544-tsd]]
