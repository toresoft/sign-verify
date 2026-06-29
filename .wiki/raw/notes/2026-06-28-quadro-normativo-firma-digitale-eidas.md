---
title: "Quadro normativo firma digitale: eIDAS, CAD, DPCM 2013, ETSI, IETF"
source: "MANUAL"
type: notes
ingested: 2026-06-28
tags:
  - eIDAS
  - CAD
  - firma digitale
  - firma elettronica qualificata
  - DPCM 2013
  - ETSI
  - trusted-lists
  - PAdES
  - CAdES
  - XAdES
  - JAdES
  - ASiC
  - normative
  - regulatory
  - RFC-5280
  - RFC-6960
  - OCSP
  - CRL
  - marche-temporali
  - TSA
  - NIST-SP-800-57
summary: >
  Riferimenti normativi completi per la verifica delle firme digitali eIDAS:
  Regolamento UE 910/2014 (eIDAS), Regolamento UE 2024/1183 (eIDAS 2.0),
  D.Lgs. 82/2005 (CAD), DPCM 22 febbraio 2013, standard tecnici ETSI
  (EN 319 102-1, CAdES, XAdES, PAdES, ASiC), standard IETF/X.509
  (RFC 5280, 5758, 6960), fonti istituzionali AgID e Commissione Europea,
  e NIST SP 800-57.
---

# Quadro normativo firma digitale: eIDAS, CAD, DPCM 2013, ETSI, IETF

## Normativa primaria europea

### Regolamento (UE) n. 910/2014 (eIDAS)

- **Art. 3 n. 12**: definizione di firma elettronica qualificata
- **Art. 25 comma 1**: riconoscimento reciproco obbligatorio delle FEQ tra Stati UE
- **Art. 26**: requisiti della firma elettronica avanzata
- **Art. 28**: requisiti dei certificati qualificati per firma elettronica
- **Art. 32**: requisiti dei servizi di validazione per FEQ
- **Art. 22**: obbligo di pubblicazione e manutenzione delle Trusted List nazionali
- **Art. 42**: presunzione di accuratezza delle marche temporali qualificate

### Regolamento (UE) 2024/1183 (eIDAS 2.0)

Modifica parziale del precedente; introduce il termination plan obbligatorio per i QTSP.

## Normativa primaria italiana

### D.Lgs. 7 marzo 2005, n. 82 (CAD — Codice dell'Amministrazione Digitale)

- **Art. 1 comma 1 lettera s)**: definizione di firma digitale
- **Art. 1 comma 1 lettera n-ter)**: definizione di dispositivo sicuro per la creazione della firma
- **Art. 20**: valore probatorio del documento informatico
- **Art. 21**: efficacia del documento con firma elettronica
- **Art. 24**: firma digitale — requisiti e validità
- **Art. 24 comma 4-bis**: firma su certificato revocato/scaduto equivale a mancata sottoscrizione

### DPCM 22 febbraio 2013 — Regole tecniche

(GU n. 117 del 21/05/2013)

- **Art. 14**: CA qualificate e accreditamento
- **Artt. 21-23**: revoca e sospensione dei certificati qualificati, gestione CRL
- **Art. 28**: KeyUsage nonRepudiation obbligatorio per certificati di sottoscrizione
- **Art. 35**: dispositivo sicuro per la creazione della firma (SSCD)
- **Artt. 47-50**: marca temporale — contenuto, chiavi TSA, requisiti
- **Art. 56**: requisiti della firma elettronica avanzata
- **Art. 61**: ambito di utilizzo della FEA
- **Art. 62 comma 1**: validità delle firme qualificate/digitali anche dopo scadenza/revoca del certificato, purché associabile a un riferimento temporale opponibile ai terzi

## Standard tecnici ETSI

### ETSI EN 319 102-1 — Procedures for Creation and Validation of AdES Digital Signatures

- Processo di validazione AdES, livelli B/T/LT/LTA
- Logica TOTAL-PASSED / INDETERMINATE / TOTAL-FAILED

### ETSI EN 319 122-1 — CAdES

Core electronic signature — formato CMS/CAdES.

### ETSI EN 319 132-1 — XAdES

Core electronic signature — formato XML/XAdES.

### ETSI EN 319 142-1 — PAdES

Core electronic signature — formato PDF/PAdES.

### ETSI EN 319 162-1 — ASiC-S / ASiC-E

Formati contenitore (ASiC).

### ETSI EN 319 412-1/2/3/5 — Policy and security requirements for TSPs issuing certificates

- **412-2**: profilo del certificato qualificato per persone fisiche (nonRepudiation, QCStatements)
- **412-3**: profilo del certificato per TSA (id-kp-timeStamping)
- **412-5**: QCStatements (QcCompliance, QcSSCD, QcLegislationCountryCodes)

### ETSI EN 319 421/422 — Policy and security requirements for TSPs providing Time-Stamping Services

### ETSI TS 119 312 — Cryptographic suites

### ETSI TS 119 322 v1.2.1

Cryptographic Suite in formato XML/JSON (usato da DSS 6.x come alternativa al blocco `<Cryptographic>` interno alla policy).

### ETSI TS 119 612 v2.3.1 / v2.4.1 — Trusted Lists

- Formato XML
- Campi ServiceTypeIdentifier, ServiceStatus
- Struttura LOTL

## Standard IETF / X.509

### RFC 5280 — Internet X.509 PKI Certificate and CRL Profile

- basicConstraints, keyUsage, AIA
- CRL Distribution Points, noRevAvail
- SubjectKeyIdentifier, AuthorityKeyIdentifier

### RFC 5758 — Additional Algorithms for DSA and ECDSA

OID per ecdsa-with-SHA256/384/512.

### RFC 6960 — OCSP Protocol

- OCSPResponseStatus, ResponderId, certHash

## Fonti istituzionali operative

### AgID — Agenzia per l'Italia Digitale

- **Trusted List italiana**: https://eidas.agid.gov.it/TL/TSL-IT.xml
- **Certificati di firma della TL** (5° e 6° certificato, GU n. 291 del 16/12/2025)
- **Documento "Tipologie di firme e sigilli elettronici"** (dicembre 2019)
- **EU LOTL**: https://ec.europa.eu/tools/lotl/eu-lotl.xml

### Commissione europea / CEF Digital

- **EU DSS library** (esig/dss su GitHub): constraint.xml di riferimento nel modulo dss-cookbook
- **Schema XSD della policy**: dss-policy-jaxb/src/main/resources/xsd/policy.xsd
- **Nota TLv6** (Decisione di esecuzione UE 2025/2164, applicabile dal 29 aprile 2026)

### NIST SP 800-57 — Recommendation for Key Management

Scadenze operative degli algoritmi:
- RSA 2048 → 2030
- SHA-256 → 2030

Usate come riferimento per le date nel blocco `<AlgoExpirationDate>`.
