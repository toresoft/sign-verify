---
title: "Trusted Lists: LOTL, TSL, formato ETSI TS 119 612 e TLv6"
source: "MANUAL"
type: articles
ingested: 2026-06-28
tags:
  - trusted-lists
  - LOTL
  - TSL
  - ETSI-TS-119-612
  - TLv6
  - AgID
  - TSL-IT
  - ServiceTypeIdentifier
  - ServiceStatus
  - qualification
  - QTSP
summary: >
  Struttura e funzionamento delle Trusted Lists europee: LOTL (List of
  Trusted Lists), TSL nazionali, formato XML ETSI TS 119 612, campi
  ServiceTypeIdentifier e ServiceStatus, migrazione TLv6, Trusted List
  italiana (AgID) e integrazione con DSS.
---

# Trusted Lists: LOTL, TSL, formato ETSI TS 119 612 e TLv6

## Architettura a due livelli

### LOTL (List of Trusted Lists)
- Pubblicata dalla Commissione Europea: https://ec.europa.eu/tools/lotl/eu-lotl.xml
- Contiene i puntatori a tutte le Trusted List nazionali degli Stati membri
- Ogni puntatore include: X509Certificate del firmatario della TL, TSLLocation
  (URL), MIME type, nome dello scheme
- Firmata con firma elettronica qualificata o sigillo qualificato
- La chiave pubblica del certificato LOTL è autenticata tramite digest
  pubblicato nella Gazzetta Ufficiale dell'Unione Europea (OJ EU)

### TSL (Trusted Service List) nazionale
- Pubblicata dallo scheme operator di ogni Stato membro
- Per l'Italia: AgID — https://eidas.agid.gov.it/TL/TSL-IT.xml
- Elenca tutti i QTSP e i servizi fiduciari qualificati

## Formato XML (ETSI TS 119 612)

La TL in formato XML contiene:

1. **Scheme information**: TSLTag, TSLSequenceNumber, ListIssueDateTime,
   NextUpdate, SchemeOperatorName, SchemeTerritory, SchemeTypeCommunityRules,
   PointersToOtherTSL, DistributionPoints

2. **TSP information** per ogni QTSP:
   - TSPName, TSPAddress, TSPInformationURI

3. **Service information** per ogni servizio:
   - ServiceTypeIdentifier (es. CA/QC, TSA, QCertESig, QCertESeal)
   - ServiceName
   - ServiceDigitalIdentity (X509Certificate + X509SKI)
   - ServiceStatus (granted, withdrawn, setbymutualagreement, etc.)
   - StatusStartingTime
   - ServiceInformationExtensions (QCStatement, Qualifications, ecc.)

4. **Service history**: storico degli status precedenti del servizio

### ServiceTypeIdentifier principali

| Identificatore | Descrizione |
|---------------|-------------|
| CA/QC | CA che emette certificati qualificati |
| TSA | Time-Stamping Authority (marca temporale) |
| QCertESig | Certificato qualificato per firma elettronica |
| QCertESeal | Certificato qualificato per sigillo elettronico |
| QCertEAuth | Certificato qualificato per autenticazione sito web |
| QESig | Servizio di firma elettronica qualificata |
| QESeal | Servizio di sigillo elettronico qualificato |
| QVal | Validazione qualificata |
| QPres | Preservazione qualificata |
| EAA | Electronic Attestation of Attributes |

### ServiceStatus principali

- **granted**: servizio attivo e qualificato
- **withdrawn**: status qualificato ritirato
- **setbymutualagreement**: status modificato per accordo bilaterale
- **deprecatedbymutualagreement**: deprecato per accordo
- **undersupervision**: sotto supervisione (transizione)
- **accredited**: accreditato (regimi pre-eIDAS)
- **historical**: non più attivo, mantenuto per finalità storiche

## TLv6 (ETSI TS 119 612 v2.3.1 / v2.4.1)

- Nuova versione del formato, applicabile dal 29 aprile 2026
- Decisione di esecuzione UE 2025/2164
- Modifiche principali: nuovi ServiceTypeIdentifier, migliore supporto
  per nuovi tipi di servizi (EAA), aggiornamenti allo schema XSD
- Il LOTL contiene il TSLVersionIdentifier che indica la versione

## Trusted List Italiana (AgID)

- URL: https://eidas.agid.gov.it/TL/TSL-IT.xml
- Certificati di firma della TL: 5° e 6° certificato (GU n. 291 del
  16/12/2025)
- AgID è lo scheme operator italiano
- La TL italiana segue il formato ETSI TS 119 612

## Uso nel DSS

La libreria DSS di CEF Digital utilizza le TL per:
1. Scaricare il LOTL dalla Commissione
2. Validare la firma del LOTL tramite l'OJ Keystore
3. Seguire i puntatori per scaricare ogni TL nazionale
4. Verificare la firma di ogni TL usando le identità nel LOTL
5. Determinare lo status qualificato di un certificato controllando
   ServiceTypeIdentifier e ServiceStatus del QTSP che lo ha emesso
