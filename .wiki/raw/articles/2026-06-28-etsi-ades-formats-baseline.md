---
title: "ETSI AdES formats and baseline signature profiles (CAdES, XAdES, PAdES, JAdES, ASiC)"
source: "https://ec.europa.eu/digital-building-blocks/sites/spaces/DIGITAL/pages/467109093/Standards+and+specifications"
type: articles
ingested: 2026-06-28
tags:
  - ETSI
  - AdES
  - CAdES
  - XAdES
  - PAdES
  - JAdES
  - ASiC
  - EN-319-102-1
  - EN-319-122-1
  - EN-319-132-1
  - EN-319-142-1
  - EN-319-162-1
  - baseline-profiles
  - B-T-LT-LTA
summary: >
  Panoramica degli standard ETSI per formati di firma elettronica avanzata
  (AdES): CAdES (CMS), XAdES (XML), PAdES (PDF), JAdES (JSON) e contenitori
  ASiC. Descrizione dei profili baseline B/T/LT/LTA e del processo di
  validazione ETSI EN 319 102-1.
---

# ETSI AdES formats and baseline signature profiles

## Quadro generale

Gli standard ETSI ESI (Electronic Signatures and Infrastructures) definiscono
i formati di firma elettronica avanzata (AdES) richiamati dal Regolamento
eIDAS e dalla Decisione di Implementazione 2015/1506/UE.

Ogni formato AdES ha due specifiche ETSI:
- **Part 1**: Building blocks e baseline signatures
- **Part 2**: Extended signatures

## Formati supportati

| Formato | Standard ETSI | Sotto-stato | Standard di base |
|---------|--------------|-------------|------------------|
| **CAdES** | EN 319 122-1/2 | CMS | IETF RFC 5652 (Cryptographic Message Syntax) |
| **XAdES** | EN 319 132-1/2 | XML | W3C XML Signature |
| **PAdES** | EN 319 142-1/2 | PDF | ISO 32000-1 (PDF) |
| **JAdES** | TS 119 182-1 | JSON | IETF RFC 7515 (JWS) |
| **ASiC** | EN 319 162-1/2 | Contenitore | ZIP con manifest |

## Profili baseline (B/T/LT/LTA)

Definiti da ETSI EN 319 102-1 e comuni a tutti i formati:

### AdES-B (Baseline)
- Firma + attributi firmati (tipo firma, data di firma, hash del certificato)
- Requisiti minimi di interoperabilità

### AdES-T (Timestamp)
- Aggiunge un timestamp (marca temporale) sulla firma
- Dimostra che la firma esisteva già a una certa data
- UT: unsigned attribute (timestamp token)

### AdES-LT (Long Term)
- Aggiunge tutti i certificati della catena di certificazione
- Aggiunge le informazioni di validità (CRL o OCSP response)
- Permette la verifica anche dopo scadenza/revoca dei certificati

### AdES-LTA (Long Term with Archive)
- Aggiunge timestamp periodici di rinnovo (evidence record)
- Permette la preservazione a lunghissimo termine
- UT: archive timestamp

## ETSI EN 319 102-1 — Validazione

Il processo di validazione è definito nella EN 319 102-1 e produce tre
possibili risultati:

- **TOTAL-PASSED**: la firma è tecnicamente valida
- **TOTAL-FAILED**: la firma non è valida (integrità compromessa, certificato
  non valido, ecc.)
- **INDETERMINATE**: non è possibile determinare la validità (es. TSL non
  disponibile, orologio non sincronizzato)

## ETSI EN 319 412 — Profili certificato

- **412-2**: Certificato qualificato per persone fisiche — nonRepudiation,
  QCStatements (QcCompliance, QcSSCD, QcLegislationCountryCodes)
- **412-3**: Certificato per TSA (id-kp-timeStamping)
- **412-5**: QCStatements (QcCompliance, QcSSCD, QcLegislationCountryCodes)

## ETSI TS 119 312 — Cryptographic suites

Definisce gli algoritmi crittografici accettabili e le loro scadenze
operative. Usato dal DSS come riferimento per il blocco
<AlgoExpirationDate> nelle policy di validazione.
