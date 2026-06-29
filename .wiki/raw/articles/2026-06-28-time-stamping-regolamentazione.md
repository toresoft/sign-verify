---
title: "Marche temporali qualificate: eIDAS Art. 41-42, ETSI EN 319 421/422, RFC 3161"
source: "MANUAL"
type: articles
ingested: 2026-06-28
tags:
  - time-stamping
  - TSA
  - TSU
  - marche-temporali
  - qualified-timestamp
  - eIDAS-Art-42
  - eIDAS-Art-41
  - ETSI-EN-319-421
  - ETSI-EN-319-422
  - RFC-3161
  - RFC-5816
  - presunzione-accuracy
summary: >
  Quadro normativo e tecnico delle marche temporali qualificate:
  Art. 41-42 eIDAS (presunzione di accuratezza e requisiti),
  ETSI EN 319 421 (policy TSA), ETSI EN 319 422 (time-stamp token profile),
  RFC 3161 (TSP protocol), Reg. (UE) 2025/1929 (binding date/time).
---

# Marche temporali qualificate: eIDAS, ETSI, RFC

## eIDAS Art. 41 — Presunzione di accuratezza

Una marca temporale qualificata gode della presunzione di:
- **Accuratezza della data e ora** indicate
- **Integrità dei dati** a cui data e ora sono legati

La presunzione opera automaticamente in tutti i 27 Stati membri: è il
contendente che deve provare l'inesattezza, non il relying party che deve
provare la correttezza.

## eIDAS Art. 42 — Requisiti

La marca temporale qualificata deve:
(a) Legare data e ora ai dati in modo da precludere modifiche non rilevabili;
(b) Essere basata su una fonte temporale accurata collegata a UTC;
(c) Essere firmata con firma elettronica avanzata o sigillo elettronico
    avanzato del QTSP.

### Regolamento (UE) 2025/1929

Stabilisce gli standard di riferimento per la conformità all'Art. 42(2).
Riferimento a ETSI EN 319 421 e 319 422. La presunzione di conformità
si applica se il QTSP segue questi standard.

## Terminologia TSA/TSU

- **TSA (Time-Stamping Authority)**: QTSP che fornisce servizi di marca
  temporale
- **TSU (Time-Stamping Unit)**: server tecnico che crea e firma le marche
  temporali per conto della TSA

## ETSI EN 319 421 — Policy e security requirements per TSA

Requisiti per TSP che emettono marche temporali (qualificate e non):
- **TIS-7.7.1-01**: Le marche temporali devono seguire il profilo definito
  in ETSI EN 319 422
- **TIS-8.1-01** [CONDITIONAL]: Se la marca è dichiarata qualificata, la
  chiave di verifica della TSU deve essere emessa con politica NCP+ (ETSI
  EN 319 411-1)
- **TIS-8.2-01** [CONDITIONAL]: Se una TSU emette marche temporali dichiarate
  qualificate, non può emettere marche non qualificate
- Il relying party deve usare la Trusted List per stabilire se la TSU/TST
  è qualificata

## ETSI EN 319 422 — Time-stamping protocol e time-stamp token profile

- Profilo del protocollo RFC 3161 e del token TST
- Include ESSCertIDv2 update (RFC 5816)
- **Sezione 9**: Requisiti aggiuntivi per marche temporali qualificate:
  - Se dichiarata qualificata, deve contenere qcStatements extension
    con statement "esi4-qtstStatement-1" (OID: id-etsi-tsts-EuQCompliance)
  - qcStatements NON deve essere marcato critical
  - Il policy field del TSTInfo deve contenere l'identifier di EN 319 421

## RFC 3161 — Time-Stamp Protocol (TSP)

- Definisce il protocollo per richiedere e ricevere marche temporali
- TimeStampReq → TimeStampResp con PKIStatus
- TSTInfo contiene: version, policy, messageImprint, serialNumber,
  genTime, accuracy, nonce, tsa, extensions

## Validazione della marca temporale

- Definita in ETSI EN 319 102-1 (non in 319 422)
- Verifica: firma del token, validità del certificato TSU, catena di
  certificazione, status qualificato tramite TL
