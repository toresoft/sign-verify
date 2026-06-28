---
type: entity
category: project
created: 2026-06-28
updated: 2026-06-28
sources:
  - sources/siva-research
---

# SiVa (open-eid/SiVa)

**Signature Validation service** maintained by the Estonian **RIA** (Information System Authority), part of the open-eid digital-identity ecosystem. EUPL-1.1, EU co-funded. Latest line **3.10.x** (May 2026). Like [[entities/sign-verify-2]] it is **validation-only** (no signing) under the eIDAS framework, which makes it the closest public peer to compare against.

## Tech stack
- **Java 17**, **Spring Boot** (embedded Tomcat), Maven wrapper, SLF4J/Logback.
- Validation engines, routed **per format**:
  - **DigiDoc4J fork of EU DSS** (`org.digidoc4j.dss:*`) → PAdES/CAdES/XAdES + ASiC-E/S, TSL/LOTL loading.
  - **DigiDoc4J** → BDOC (TimeMark `LT_TM` + TimeStamp).
  - **JDigiDoc** → legacy DDOC / DIGIDOC-XML 1.0–1.3 (validated against static SK CA certs).
  - **X-Road CLI** → X-Road ASiC-E.
- Contrast: [[entities/sign-verify-2]] uses **upstream [[entities/dss]] 6.4** directly (no fork lag).

## Architecture
**Modular monolith** (single `siva-webapp`, port 8080), not microservices despite the "service" naming. Pipeline: Web Gateway → Validation Proxy (routes by document type) → format validation services: **Generic** (ASiC/PAdES/CAdES/XAdES), **Timemark-container** (BDOC/DDOC), **TST** (ASiC-S timestamp token), **Hashcode** proxy. Plus Report Signing, Statistics, TSL Loader, DDOC data-files extraction. WAR deployments need **one Tomcat per service** to avoid JAR conflicts from the multi-library design.

## API & reports
- Sync-only REST/JSON (SOAP removed in 3.10): `POST /validate`, `POST /validateHashcode`, `POST /getDataFiles`. **No async/callbacks/batch.**
- Policies: **POLv3** (type-agnostic: QES/AdES/AdES-QC all pass) and **POLv4** (default: qualified-only, QSCD required). No integrator-supplied DSS constraint XML.
- Report tiers: **Simple** / **Detailed** (+ `validationProcess`, optional `validationReportSignature`) / **Diagnostic** (raw DSS `diagnosticData`).
- Report fields align to ETSI EN 319 102-1: `indication`/`subIndication`, **`signatureLevel`** qualification enum (`QESIG, QESEAL, ADESIG_QC, ADESEAL_QC, ADES_QC, ADESIG, ADESEAL, ADES, NOT_ADES_QC_QSCD, NA`), `signedBy`, `claimedSigningTime`, `bestSignatureTime`, `timeStampTokens[]` (level `QTSA`/`TSA`), `archiveTimeStamps[]`, typed `certificates[]`, `validatedDocument` (filename+hash), `warnings[]`.

## Differentiators vs a plain DSS wrapper
- **Signed validation report** wrapped in a signed **ASiC-E** container (PKCS#11/PKCS#12) → non-repudiation of the verdict.
- **Hashcode validation** (validate by `filename`+`hashAlgo`+`hash`, no original file).
- **Qualification level** enum surfaced explicitly.
- **DDOC datafile extraction** endpoint.
- **Monitoring**: `/monitoring/{health,heartbeat,version,prometheus}`.
- **Statistics** per validation (syslog-JSON / optional Google Analytics; not a queue).

## Maturity
RIA production via X-Road; public demos `siva-demo.eesti.ee/V3/`, `siva-arendus.eesti.ee/V3/`. System tests in a separate repo (**open-eid/Siva-test**, RestAssured/Groovy) over a real signed-document corpus (DDOC/BDOC/ASiC/PAdES B/T/LT/LTA, serial/parallel, valid+invalid, ≤9 MB); perf via **Gatling** (open-eid/SiVa-perftests); CI on GitHub Actions.

## Related
- [[analyses/siva-vs-sign-verify-2]] — head-to-head comparison and improvement backlog.
- [[entities/sign-verify-2]] · [[entities/dss]] · [[concepts/trusted-lists]] · [[concepts/validation-profiles]]
- [[concepts/etsi-en-319-102-1-validation]] · [[concepts/reports]] · [[concepts/rfc5544-tsd]]
