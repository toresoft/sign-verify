# 0. Glossary

← [Index](README.md) · → [1. Build and configuration](01-build-configuration.md)

eIDAS and DSS bring their own vocabulary. This page defines the terms used
throughout this documentation and the API responses, so you don't have to
piece them together from five different pages.

## Regulation and trust model

| Term | Meaning |
|------|---------|
| **eIDAS** | EU Regulation 910/2014 on electronic identification and trust services. Defines the legal tiers of electronic signatures and the cross-border trust framework this service validates against. |
| **TSP** (Trust Service Provider) | An entity (certification authority, timestamp authority, etc.) authorised under eIDAS to issue qualified certificates, timestamps or other trust services. |
| **LOTL** (List Of Trusted Lists) | The EU's master list of national Trusted Lists, published by the European Commission. The starting point of the whole trust chain; see [4. Trusted Certificates](04-trusted-certificates.md). |
| **TSL** (Trusted (service status) List) | A national list, signed by a Member State, naming the TSPs it supervises and their services. The service downloads and mirrors these. |
| **OJ** (Official Journal of the EU) | Publishes the certificates used to validate the LOTL's own signature (the trust anchor of the trust anchor). See [3.7 OJ keystore](04-trusted-certificates.md#37-oj-keystore-lotl-trust-anchor). |

## Signature formats

| Term | Meaning |
|------|---------|
| **PAdES** | PDF Advanced Electronic Signatures: signature embedded inside a PDF. |
| **CAdES** | CMS Advanced Electronic Signatures: typically a detached `.p7m` envelope around the original file. |
| **XAdES** | XML Advanced Electronic Signatures: signature embedded inside or alongside an XML document. |
| **JAdES** | JSON Advanced Electronic Signatures: the JSON equivalent of the above. |
| **ASiC-S / ASiC-E** | Associated Signature Containers: a ZIP-based wrapper holding one signed file (`-S`) or several signed files plus a manifest (`-E`). |
| **TSD** (RFC 5544 TimeStampedData) | A CMS structure wrapping arbitrary content with a timestamp, common in Italian PA tooling (ArubaSign, GoSign, Namirial). See [5.5 TSD extraction](06-file-extraction.md#55-tsd-extraction). |

## Legal tiers (AdES vs QES)

| Term | Meaning |
|------|---------|
| **AdES** | Advanced Electronic Signature: meets baseline eIDAS requirements (uniquely linked to the signer, capable of identifying them, created with data under the signer's sole control, detects tampering). Does **not** require a qualified certificate. |
| **QES** | Qualified Electronic Signature: an AdES created with a **qualified** certificate on a qualified signature creation device. Legally equivalent to a handwritten signature across the EU. |
| **AdESig / AdESeal** | Advanced signature (natural person) vs advanced seal (legal person). |
| **QESig / QESeal** | The qualified counterparts of the above. |

The `signatureLevel` field in the API response reports exactly this tier.
See [Enum values](05-signature-verification.md#enum-values).

## Validation outcome

| Term | Meaning |
|------|---------|
| **indication** | The overall validation outcome as defined by ETSI EN 319 102-1: `TOTAL_PASSED`, `TOTAL_FAILED`, or `INDETERMINATE`. |
| **subIndication** | The detailed reason behind a non-`TOTAL_PASSED` indication (e.g. `SIG_CRYPTO_FAILURE`, `NO_CERTIFICATE_CHAIN_FOUND`). |
| **signatureLevel** | The eIDAS qualification of the signature (`QESIG`, `ADESIG_QC`, …). Orthogonal to `indication`: a signature can be cryptographically valid (`TOTAL_PASSED`) yet only `ADESIG_QC` if it was never backed by a qualified certificate. |

## Validation policy

| Term | Meaning |
|------|---------|
| **Validation policy** | An XML document listing every constraint DSS checks (signature integrity, certificate chain, revocation, timestamps, cryptographic strength…) and how strictly each one is enforced. |
| **Profile** | This service's wrapper around a validation policy, selectable per request (`BASIC` / `STANDARD` / `STRICT` / `CUSTOM`). See [4.2 Validation profiles](05-signature-verification.md#42-validation-profiles). |
| **Level** (`FAIL` / `WARN` / `INFORM` / `IGNORE`) | The severity assigned to a single constraint: whether failing it blocks the outcome, is merely reported, or is not checked at all. |

## Timestamps and long-term validation

| Term | Meaning |
|------|---------|
| **TSA** (Time Stamp Authority) | A trust service that issues timestamps proving a piece of data existed at a given time. |
| **T / LT / LTA** | Baseline signature levels of increasing durability: **T** (Timestamp) proves existence at signing time; **LT** (Long-Term) embeds enough revocation data to validate years later even if the TSA disappears; **LTA** (Long-Term Archive) periodically re-timestamps the signature to survive cryptographic algorithm decay. |
| **Evidence record** (RFC 4998 / 6283) | The mechanism behind LTA renewal: a chain of timestamps that keeps a signature provably valid over decades. Surfaced in the API as `archiveTimestamps[]`; see [Document timestamps vs archive timestamps](05-signature-verification.md#document-timestamps-vs-archive-timestamps). |

## DSS and reports

| Term | Meaning |
|------|---------|
| **DSS** (Digital Signature Services) | The EU-maintained open-source library ([github.com/esig/dss](https://github.com/esig/dss)) this service wraps to perform the actual eIDAS-compliant validation. |
| **Simple report** | DSS's concise, per-signature pass/fail summary. |
| **Detailed report** | DSS's full per-constraint breakdown (which checks ran, and why each passed or failed). |
| **Diagnostic data** | The raw data DSS collected during validation (certificates, revocation data, timestamps…), useful for debugging. |
| **ETSI report** | A validation report following the standard ETSI TS 119 102-2 XML schema, for interoperability with other eIDAS tooling. |
