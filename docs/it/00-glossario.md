# 0. Glossario

← [Indice](README.md) · → [1. Compilazione e configurazione](01-build-configurazione.md)

eIDAS e DSS portano con sé un vocabolario tutto loro. Questa pagina definisce
i termini usati in tutta la documentazione e nelle risposte API, così non
serve ricostruirli saltando tra cinque pagine diverse.

## Regolamento e modello di fiducia

| Termine | Significato |
|---------|-------------|
| **eIDAS** | Regolamento UE 910/2014 su identificazione elettronica e servizi fiduciari. Definisce i livelli legali delle firme elettroniche e il framework di fiducia transfrontaliero contro cui questo servizio valida. |
| **TSP** (Trust Service Provider) | Un soggetto (autorità di certificazione, autorità di marcatura temporale, ecc.) autorizzato secondo eIDAS a emettere certificati qualificati, marche temporali o altri servizi fiduciari. |
| **LOTL** (List Of Trusted Lists) | La lista madre UE delle Trusted List nazionali, pubblicata dalla Commissione Europea. Il punto di partenza dell'intera catena di fiducia; vedi [4. Trusted Certificates](04-trusted-certificates.md). |
| **TSL** (Trusted (service status) List) | Una lista nazionale, firmata da uno Stato membro, che elenca i TSP da esso vigilati e i loro servizi. Il servizio le scarica e le tiene in mirror. |
| **OJ** (Gazzetta Ufficiale UE) | Pubblica i certificati usati per validare la firma della LOTL stessa (l'ancora di fiducia dell'ancora di fiducia). Vedi [3.7 OJ keystore](04-trusted-certificates.md#37-oj-keystore-lotl-trust-anchor). |

## Formati di firma

| Termine | Significato |
|---------|-------------|
| **PAdES** | PDF Advanced Electronic Signatures: firma incorporata in un PDF. |
| **CAdES** | CMS Advanced Electronic Signatures: tipicamente una busta `.p7m` separata attorno al file originale. |
| **XAdES** | XML Advanced Electronic Signatures: firma incorporata in o accanto a un documento XML. |
| **JAdES** | JSON Advanced Electronic Signatures: l'equivalente JSON dei precedenti. |
| **ASiC-S / ASiC-E** | Associated Signature Containers: un contenitore basato su ZIP con un file firmato (`-S`) o più file firmati più un manifest (`-E`). |
| **TSD** (RFC 5544 TimeStampedData) | Una struttura CMS che avvolge un contenuto arbitrario con una marca temporale, comune negli strumenti della PA italiana (ArubaSign, GoSign, Namirial). Vedi [5.5 Estrazione da TSD](06-estrazione-file.md#55-estrazione-da-tsd). |

## Livelli legali (AdES vs QES)

| Termine | Significato |
|---------|-------------|
| **AdES** | Advanced Electronic Signature: soddisfa i requisiti minimi eIDAS (legata univocamente al firmatario, capace di identificarlo, creata con dati sotto il suo controllo esclusivo, rileva manomissioni). **Non** richiede un certificato qualificato. |
| **QES** | Qualified Electronic Signature: una AdES creata con un certificato **qualificato** su un dispositivo qualificato di creazione firma. Equivalente legalmente a una firma autografa in tutta la UE. |
| **AdESig / AdESeal** | Firma avanzata (persona fisica) contro sigillo avanzato (persona giuridica). |
| **QESig / QESeal** | Le controparti qualificate dei precedenti. |

Il campo `signatureLevel` nella risposta API riporta esattamente questo
livello. Vedi [Valori degli enum](05-verifica-firme.md#valori-degli-enum).

## Esito della validazione

| Termine | Significato |
|---------|-------------|
| **indication** | L'esito complessivo della validazione secondo ETSI EN 319 102-1: `TOTAL_PASSED`, `TOTAL_FAILED` o `INDETERMINATE`. |
| **subIndication** | La motivazione dettagliata dietro un `indication` diverso da `TOTAL_PASSED` (es. `SIG_CRYPTO_FAILURE`, `NO_CERTIFICATE_CHAIN_FOUND`). |
| **signatureLevel** | La qualificazione eIDAS della firma (`QESIG`, `ADESIG_QC`, …). Ortogonale a `indication`: una firma può essere crittograficamente valida (`TOTAL_PASSED`) ma solo `ADESIG_QC` se non è mai stata supportata da un certificato qualificato. |

## Policy di validazione

| Termine | Significato |
|---------|-------------|
| **Validation policy** | Un documento XML che elenca ogni vincolo controllato da DSS (integrità della firma, catena dei certificati, revoca, marche temporali, robustezza crittografica…) e con quanto rigore ciascuno viene applicato. |
| **Profilo** | Il wrapper di questo servizio attorno a una validation policy, selezionabile per singola richiesta (`BASIC` / `STANDARD` / `STRICT` / `CUSTOM`). Vedi [4.2 Profili di validazione](05-verifica-firme.md#42-profili-di-validazione). |
| **Level** (`FAIL` / `WARN` / `INFORM` / `IGNORE`) | La severità assegnata a un singolo vincolo: se il suo fallimento blocca l'esito, viene solo riportato, o non viene proprio controllato. |

## Marche temporali e validazione a lungo termine

| Termine | Significato |
|---------|-------------|
| **TSA** (Time Stamp Authority) | Un servizio fiduciario che emette marche temporali a prova dell'esistenza di un dato in un determinato istante. |
| **T / LT / LTA** | Livelli di firma baseline a durabilità crescente: **T** (Timestamp) prova l'esistenza al momento della firma; **LT** (Long-Term) incorpora dati di revoca sufficienti a validare anni dopo anche se la TSA non esiste più; **LTA** (Long-Term Archive) ri-marca periodicamente la firma per sopravvivere al decadimento degli algoritmi crittografici. |
| **Evidence record** (RFC 4998 / 6283) | Il meccanismo dietro il rinnovo LTA: una catena di marche temporali che mantiene una firma dimostrabilmente valida per decenni. Esposto nell'API come `archiveTimestamps[]`; vedi [Timestamp del documento vs timestamp di archivio](05-verifica-firme.md#timestamp-del-documento-vs-timestamp-di-archivio). |

## DSS e report

| Termine | Significato |
|---------|-------------|
| **DSS** (Digital Signature Services) | La libreria open-source mantenuta dalla UE ([github.com/esig/dss](https://github.com/esig/dss)) che questo servizio utilizza per eseguire la validazione conforme a eIDAS. |
| **Simple report** | Il riepilogo conciso di DSS, pass/fail per ciascuna firma. |
| **Detailed report** | Il report dettagliato di DSS, vincolo per vincolo (quali controlli sono stati eseguiti e perché ciascuno è passato o fallito). |
| **Diagnostic data** | I dati grezzi raccolti da DSS durante la validazione (certificati, dati di revoca, marche temporali…), utili per il debug. |
| **ETSI report** | Un report di validazione secondo lo schema XML standard ETSI TS 119 102-2, per l'interoperabilità con altri strumenti eIDAS. |
