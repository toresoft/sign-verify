---
title: "Punti di miglioramento per sign-verify-2 (ispirati a SiVa)"
type: output
format: playbook
sources:
  - analyses/siva-vs-sign-verify-2
  - entities/siva
  - sources/siva-research
generated: 2026-06-28
---

# Punti di miglioramento per sign-verify-2 (da open-eid/SiVa)

Backlog operativo derivato dal confronto [[../wiki/analyses/siva-vs-sign-verify-2]]. Ogni voce: cosa fa SiVa, gap attuale, intervento, riferimenti.

## P1 — alto valore

### 1. Report arricchito `signatures[]` / `timestamps[]`
- **SiVa**: per-firma `signedBy`, `claimedSigningTime`, `bestSignatureTime`, `signatureLevel`; array `timeStampTokens[]` (livello QTSA/TSA), `archiveTimeStamps[]` (LTA), `certificates[]` tipizzati (SIGNING/REVOCATION/SIGNATURE_TIMESTAMP/ARCHIVE_TIMESTAMP), `validatedDocument` (hash), `warnings[]`.
- **Gap**: `ValidationResult` è piatto (`signatureFormat/indication/subIndication/signatureCount` + mappa `reports`).
- **Intervento**: estendere `openapi.yaml` (design-first) con `signatures[]`/`timestamps[]`; aggiornare assembler dal `SimpleReport`/`DiagnosticData`. È la **Phase 4** del piano TSD → riusare [[../wiki/analyses/tsd-dto-mapping]]. Mantenere i campi piatti per retro-compatibilità; `OpenApiContractIT` come guardia.

### 2. Livello di qualifica eIDAS (`signatureLevel`)
- **SiVa**: enum `QESIG, QESEAL, ADESIG_QC, ADESEAL_QC, ADES_QC, ADESIG, ADESEAL, ADES, NOT_ADES_QC_QSCD, NA`.
- **Gap**: non esposto.
- **Intervento**: mappare `SimpleReport.getSignatureQualification(sigId)` di DSS in un campo `signatureLevel` per firma. Sforzo basso (dato già calcolato da DSS). Da fare insieme al punto 1.

### 3. Report di validazione firmato (non-ripudio)
- **SiVa**: Detailed report incapsulato in **ASiC-E firmato** (PKCS#11/PKCS#12) → il verdetto è esso stesso firmato dal validatore.
- **Gap**: nessun report firmato.
- **Intervento**: nuovo `ReportSignerPort` + adapter DSS (`ASiCWithXAdESService`/`PAdESService` o `SignatureService`), chiave da keystore (riusare `SecretCipherPort`/config esistente). Opzionale via parametro `reportType=signed`. Valore alto per PA (prova del controllo).

## P2 — medio valore

### 4. Hashcode validation
- **SiVa**: `POST /validateHashcode` — valida per `filename`+`hashAlgo`+`hash`, senza il file originale (privacy/banda).
- **Gap**: serve sempre il documento completo.
- **Intervento**: usare `eu.europa.esig.dss.model.DigestDocument` come detached content (stessa tecnica del resolver imprint già scritto in `TsdAwareValidatorAdapter`). Endpoint dedicato o flag su `/verifications`.

### 5. Corpus di conformità + load test
- **SiVa**: repo separato `open-eid/Siva-test` (RestAssured) con documenti reali — PAdES/CAdES/XAdES/ASiC, profili B/T/LT/LTA, serie/parallele, validi+invalidi, ≤9 MB; perf con **Gatling** (`SiVa-perftests`).
- **Gap**: corpus limitato.
- **Intervento**: costruire corpus firmato reale/sintetico (aggancio [[../wiki/analyses/cades-pades-test-corpus]], [[../wiki/analyses/tsd-test-corpus]]); IT Failsafe end-to-end + load test (Gatling/k6).

### 6. Audit + statistiche d'uso
- **SiVa**: statistiche per-validazione (tipo formato, esito) via syslog-JSON / Google Analytics.
- **Gap**: `AuditService` **non cablato** nei path operativi (gap noto).
- **Intervento**: cablare `AuditService`; aggiungere metriche d'uso (Micrometer: counter per formato/esito).

### 7. Validazione timestamp-token dedicata
- **SiVa**: *TST Validation Service* per ASiC-S e token RFC 3161 nudi.
- **Gap**: sign-verify-2 gestisce TSD RFC 5544 (appena aggiunto) ma non un path dedicato `.tsr`/ASiC-S.
- **Intervento**: endpoint/strato dedicato come suggerito in [[../wiki/analyses/verifica-file-tsd]] (`DetachedTimestampValidator`).

## P3 — basso sforzo

### 8. Health indicator dedicati
- **SiVa**: `/monitoring/{health,heartbeat,version,prometheus}` con stato dipendenze.
- **Intervento**: `HealthIndicator` per **freschezza TSL** e disponibilità DSS; esporre build/version (Actuator `info`). Collega [[../wiki/concepts/tsl-hot-swap-refresh]].

### 9. Semantica policy QES-only vs AdES
- **SiVa**: POLv4 (default, QES-only) vs POLv3 (AdES-permissiva).
- **Intervento**: documentare/allineare i preset BASIC/STANDARD/STRICT alla distinzione eIDAS; esplicitare nella descrizione OpenAPI.

## Da NON copiare da SiVa
- Fork DSS `org.digidoc4j.dss` (lag upstream) — restare su **upstream DSS 6.4**.
- Sync-only — l'async+webhook è un vantaggio.
- Un Tomcat per servizio (conseguenza del multi-libreria) — architettura esagonale a servizio singolo lo evita.
- Trust estone hardcoded (DDOC) — non neutrale eIDAS.

## Riferimenti
- [[../wiki/entities/siva]] · [[../wiki/analyses/siva-vs-sign-verify-2]] · [[../wiki/sources/siva-research]]
- Docs SiVa: https://open-eid.github.io/SiVa/siva3/interfaces/ · https://open-eid.github.io/SiVa/siva3/appendix/validation_policy/
