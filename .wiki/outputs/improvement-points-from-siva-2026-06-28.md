---
title: "Punti di miglioramento per sign-verify-2 (ispirati a SiVa)"
type: output
format: playbook
sources:
  - analyses/siva-vs-sign-verify-2
  - entities/siva
  - sources/siva-research
  - sources/SRC-2026-06-28-001
generated: 2026-06-28
updated: 2026-06-28
notes: "Re-synthesized after audit (2026-06-28) — aligned with code state post commits dd9878f/6912fc5/4b4c262. Body previously contradicted on points 1/2/6/8."
---

# Punti di miglioramento per sign-verify-2 (da open-eid/SiVa)

Backlog operativo derivato dal confronto [[../wiki/analyses/siva-vs-sign-verify-2]], verificato contro il codice attuale (audit 2026-06-28). Legenda: ✅ = già fatto, 🟡 = parzialmente fatto, ❌ = da fare.

## Già fatto (verificato contro codice)

### ✅ Livello di qualifica eIDAS (`signatureLevel`)
- **Commit:** `dd9878f feat(api): expose signatures[]/timestamps[] with eIDAS qualification`
- **Come:** `SignatureSummary.signatureLevel` ← `SimpleReport.getSignatureQualification(id).name()`. Enum DSS (QESIG/QESEAL/ADESIG_QC/…/NA) serializzato in ogni `signatures[].signatureLevel`.
- **Test:** `DssValidatorAdapterTest#enriches_response_with_signatures_and_qualification` (verde).

### ✅ Health indicator dedicati
- **Commit:** `4b4c262 feat(health): enrich actuator with git, TSL, job-queue and DB details` (+ preesistenti)
- **Come:** `TslReadinessIndicator` (lastRefreshStatus/At, cert count, readiness gating OUT_OF_SERVICE), `DssHealthIndicator` (CertificateVerifier wired), `JobQueueHealthIndicator`, `/actuator/info` con `BuildProperties`+`GitProperties`.
- **Sicurezza:** `show-details: when-authorized` (solo PRIVILEGED). Test: 4/4 verdi.

### ✅ Report arricchito (parziale — residuo sotto)
- **Commit:** `dd9878f` + `6912fc5 fix(dss): attach per-signature timestamps`
- **Esposto:** `signedBy`, `bestSignatureTime`, `signatureFormat`, `signatureLevel`, `signatures[].timestamps[]` con livello (QTSA/TSA), `timestamps[]` top-level con qualification.
- **Resta da fare:** `claimedSigningTime`, array separato `archiveTimeStamps[]`, `certificates[]` tipizzati per firma (SIGNING/REVOCATION/SIGNATURE_TIMESTAMP/ARCHIVE_TIMESTAMP).

### ✅ Audit (parziale)
- **Cablaggio esistente:** `TslRefreshScheduler` (6× `audit.log`), `AsyncVerificationController` (access-denial), `TslController` (manual refresh). Totale 13 file referenziano `audit`.
- **Mancante:** sync `VerificationController` e `ValidationWorker` — i path operativi principali non emettono audit.

## Da fare (nessun codice o parziale)

### ❌ Report di validazione firmato (non-ripudio) — P1
- **SiVa:** Detailed report in **ASiC-E firmato** (PKCS#11/PKCS#12) → verdetto non ripudiabile.
- **Gap:** nessun report firmato.
- **Intervento:** nuovo `ReportSignerPort` + adapter DSS (`ASiCWithXAdESService` o `SignatureService`), chiave da keystore. Opzionale via parametro `reportType=signed`. Alto valore per PA.
- **Sforzo:** medio

### ❌ Hashcode validation — P2
- **SiVa:** `POST /validateHashcode` — valida per `filename`+`hashAlgo`+`hash`, senza il file originale (privacy/banda).
- **Gap:** serve sempre il documento completo.
- **Intervento:** usare `DigestDocument` DSS come detached content. Endpoint dedicato o flag su `/verifications`. La tecnica è la stessa del resolver imprint in `TsdAwareValidatorAdapter`.
- **Sforzo:** medio

### ❌ Corpus di conformità + load test — P2
- **SiVa:** repo `open-eid/Siva-test` (RestAssured) + `SiVa-perftests` (Gatling). Documenti reali PAdES/CAdES/XAdES/ASiC B/T/LT/LTA, validi+invalidi, ≤9 MB.
- **Gap:** corpus limitato a pochi sample in `src/test/resources/assets/`.
- **Intervento:** costruire corpus firmato (aggancio [[../wiki/analyses/cades-pades-test-corpus]], [[../wiki/analyses/tsd-test-corpus]]); IT Failsafe e2e + load test Gatling/k6.
- **Sforzo:** medio, alto valore di fiducia

### 🟡 Report arricchito — residuo — P1
- **Già esposto:** `signedBy`, `bestSignatureTime`, `signatureLevel`, `signatureFormat`, `indication`/`subIndication`, `signatures[].timestamps[]` (level QTSA/TSA), `timestamps[]` top-level.
- **Mancante vs SiVa:**
  - `claimedSigningTime` (orario dichiarato dal firmatario)
  - `archiveTimeStamps[]` come array separato (per LTA)
  - `certificates[]` tipizzati per firma (SIGNING/REVOCATION/SIGNATURE_TIMESTAMP/ARCHIVE_TIMESTAMP)
- **Intervento:** estendere `SimpleReportMapper` e `SignatureSummary`; aggiornare `openapi.yaml` (design-first) con i nuovi campi; `OpenApiContractIT` come guardia.
- **Sforzo:** medio (struttura DTO già in posto)

### 🟡 Cablare audit nei path sincroni — P2
- **Già cablato:** `TslRefreshScheduler`, `AsyncVerificationController` (access-denial), `TslController`.
- **Mancante:** sync `VerificationController` (POST `/verifications`), `ValidationWorker` (async processing path).
- **Intervento:** emettere `audit.log` su verifica sincrona e processamento worker. Aggiornare `entities/sign-verify-2.md` "Known gaps".
- **Sforzo:** basso

### ❌ Semantica policy QES vs AdES — P3
- **SiVa:** POLv4 default QES-only vs POLv3 permissiva (tutti i livelli legali).
- **Gap:** preset BASIC/STANDARD/STRICT non documentano la distinzione eIDAS QES vs AdES.
- **Intervento:** documentare/allineare i preset; esplicitare nella descrizione OpenAPI del campo `indication`/policy.
- **Sforzo:** basso

### ❌ Validazione timestamp-token dedicata — P2
- **SiVa:** *TST Validation Service* per ASiC-S e token RFC 3161 nudi.
- **Gap:** `TsdAwareValidatorAdapter` gestisce TSD RFC 5544 ma non un path dedicato `.tsr`/ASiC-S standard.
- **Intervento:** endpoint/strato per token detached con `DetachedTimestampValidator` (come suggerito in [[../wiki/analyses/verifica-file-tsd]]).
- **Sforzo:** medio

## Da NON copiare da SiVa
- Fork DSS `org.digidoc4j.dss` (lag upstream) — restare su **upstream DSS 6.4**.
- Sync-only — l'async+webhook è un vantaggio.
- Un Tomcat per servizio (conseguenza del multi-libreria) — architettura esagonale a servizio singolo lo evita.
- Trust estone hardcoded (DDOC) — non neutrale eIDAS.

## Riferimenti
- [[../wiki/analyses/siva-vs-sign-verify-2]] (analisi wiki corretta 2026-06-28)
- [[../wiki/entities/siva]] · [[../wiki/entities/sign-verify-2]] · [[../wiki/sources/siva-research]]
- Raw docs SiVa: [[../wiki/sources/SRC-2026-06-28-001]] (60 file @ `348a6b2`, EUPL-1.1)
