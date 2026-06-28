---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/dss-timestamp-api-research
---

# DSS 6.4 — API per timestamp embedded in CAdES

## Gerarchia degli oggetti

```
Reports
  ├── DiagnosticData      → dati grezzi, policy-independent
  ├── SimpleReport        → sintesi human/machine (indication, qualified)
  ├── DetailedReport      → building blocks ETSI EN 319 102-1 
  └── ETSIValidationReport → TS 119 102-2 machine-readable
```

## Iterazione timestamp per DTO mapping

### Entrypoint: DiagnosticData

```java
DiagnosticData dd = reports.getDiagnosticData();

// Tutti i timestamp del documento (cross-signature)
List<TimestampWrapper> all = dd.getTimestampList();

// Timestamp di una firma specifica
List<TimestampWrapper> forSig = dd.getTimestampList(signatureId);

// Per tipo
List<TimestampWrapper> archives = dd.getTimestampsByType(TimestampType.ARCHIVE_TIMESTAMP);
```

### Per firma: SignatureWrapper

```java
for (SignatureWrapper sig : dd.getSignatures()) {
    // ⚠️ getSignatures() include counter-signatures!
    // Per soli master: dd.getAllSignatures()

    // Metodi convenienti per livello:
    sig.getContentTimestamps()   // CONTENT_TIMESTAMP (pre-firma, raro)
    sig.getTLevelTimestamps()    // SIGNATURE_TIMESTAMP (livello T)
    sig.getALevelTimestamps()    // ARCHIVE_TIMESTAMP (livello LTA)
    sig.getTimestampList()       // tutti

    // Formato firma (es. "CAdES_BASELINE_LT")
    // → da SimpleReport.getSignatureFormat(sig.getId())
}
```

### Per timestamp: TimestampWrapper

```java
TimestampWrapper ts = ...;

String id               = ts.getId();
TimestampType type      = ts.getType();        // enum (vedi sotto)
Date productionTime     = ts.getProductionTime();   // ora TSA → usa per DTO
String tsaName          = ts.getTSAGeneralNameValue();   // da TSTInfo.tsa
boolean intact          = ts.isMessageImprintDataIntact(); // hash check
boolean tsaMatch        = ts.isTSAGeneralNameMatch();

// Solo per archive timestamps (CAdES LTA):
ArchiveTimestampType atsType = ts.getArchiveTimestampType(); // v2/v3
boolean atsHashIndexValid    = ts.isAtsHashIndexValid();
```

### Indication da SimpleReport

```java
SimpleReport sr = reports.getSimpleReport();

// Funziona sia per signatureId che per timestampId
Indication ind         = sr.getIndication(id);      // TOTAL_PASSED / INDETERMINATE / FAILED
SubIndication subInd   = sr.getSubIndication(id);   // NO_CERTIFICATE_CHAIN_FOUND, HASH_FAILURE, ...
boolean valid          = sr.isValid(id);             // shorthand

// Metadati timestamp
Date prodTime          = sr.getProductionTime(tsId);
String producedBy      = sr.getProducedBy(tsId);    // nome TSA da TrustedList (può essere null)
TimestampQualification qual = sr.getTimestampQualification(tsId); // QTSA / TSA / NA

// Firma
SignatureLevel format   = sr.getSignatureFormat(sigId);       // CAdES_BASELINE_LT etc.
Date bestSigTime        = sr.getBestSignatureTime(sigId);     // tempo LT/LTA (giuridicamente rilevante)
Date claimedSigTime     = sr.getSigningTime(sigId);           // tempo dichiarato dal firmatario
int sigCount            = sr.getSignaturesCount();
int validSigCount       = sr.getValidSignaturesCount();
```

### Two-level indication da DetailedReport

```java
DetailedReport dr = reports.getDetailedReport();

// Livello base (BBB crypto check)
Indication basicInd  = dr.getBasicTimestampValidationIndication(tsId);
SubIndication basicSub = dr.getBasicTimestampValidationSubIndication(tsId);

// Livello archive data (contesto LTA)
Indication archInd   = dr.getArchiveDataTimestampValidationIndication(tsId);
SubIndication archSub = dr.getArchiveDataTimestampValidationSubIndication(tsId);

// Qualification
TimestampQualification qual = dr.getTimestampQualification(tsId);
```

## Enum TimestampType — valori CAdES rilevanti

| Enum | OID CMS | Livello | Quando appare |
|---|---|---|---|
| `CONTENT_TIMESTAMP` | `id-aa-ets-contentTimestamp` | pre-B | raro |
| `SIGNATURE_TIMESTAMP` | `id-aa-signatureTimeStampToken` | T | firma-T |
| `VALIDATION_DATA_TIMESTAMP` | `id-aa-ets-escTimeStamp` | X | raro |
| `ARCHIVE_TIMESTAMP` | `id-aa-ets-archiveTimestamp` | LTA | TSD / LTA |
| `CONTAINER_TIMESTAMP` | — | ASiC | solo ASiC |
| `DOCUMENT_TIMESTAMP` | — | PAdES | solo PAdES |

Per CAdES-T: `SIGNATURE_TIMESTAMP`. Per LTA (tipico nei .tsd con archive-ts): `ARCHIVE_TIMESTAMP`.

## Algoritmo DTO: tabella di mapping

| Campo DTO | Sorgente DSS |
|---|---|
| `timestamps[].id` | `TimestampWrapper.getId()` |
| `timestamps[].type` | `TimestampWrapper.getType()` (enum) |
| `timestamps[].productionTime` | `SimpleReport.getProductionTime(id)` |
| `timestamps[].producedBy` | `SimpleReport.getProducedBy(id)` |
| `timestamps[].indication` | `SimpleReport.getIndication(id)` |
| `timestamps[].subIndication` | `SimpleReport.getSubIndication(id)` |
| `timestamps[].qualification` | `SimpleReport.getTimestampQualification(id)` |
| `timestamps[].algorithm` | `DiagnosticData` via `XmlBasicSignature` — **NON in SimpleReport** |
| `signature.format` | `SimpleReport.getSignatureFormat(sigId)` |
| `signature.bestSignatureTime` | `SimpleReport.getBestSignatureTime(sigId)` |
| `signature.claimedSigningTime` | `SimpleReport.getSigningTime(sigId)` |
| `signature.indication` | `SimpleReport.getIndication(sigId)` |
| `overallValid` | `sr.getValidSignaturesCount() == sr.getSignaturesCount()` |

**Nota:** L'algoritmo del timestamp (es. SHA-256) non è in SimpleReport: va estratto da `DiagnosticData.getTimestampById(id).getDigestAlgorithm()`.

## Aggregazione result multipli

DSS non aggrega automaticamente. Scelte comuni:
- `worst-of`: result finale = peggior indication tra tutte le firme+timestamp
- `any-passed`: valido se almeno una firma è TOTAL_PASSED
- La semantica va documentata nell'API OpenAPI

`INDETERMINATE` ≠ `FAILED`. Significa dati di validazione incompleti (TSA non in TL). La decisione se trattarlo come errore è **application-level policy**.

## Counter-signatures: trappola conteggio

```java
// ⚠️ getSignatures() include counter-firme — conta errata per "quanti firmatari"
dd.getSignatures().size()      // SBAGLIATO per conteggio firmatari

dd.getAllSignatures().size()    // solo master signatures ✓
dd.getAllCounterSignatures()    // solo counter-signatures
sig.isCounterSignature()        // flag per distinguere
```

## Riferimento SiVa (implementazione di riferimento)

Estonian SiVa service (DSS-based, open-source) usa:
- Firme: `indication`, `subIndication`, `signatureFormat`, `signatureLevel`, `signedBy`, `claimedSigningTime`, `bestSignatureTime`, `signatureScopes[]`, `info.archiveTimeStamps[]`
- Timestamp standalone: `signedTime` (= `productionTime`), `signedBy` (= `producedBy`), `indication`, `timestampLevel`

## Related

- [[concepts/reports]] — struttura Reports DSS
- [[concepts/etsi-en-319-102-1-validation]] — algoritmo normativo alla base dell'indication
- [[analyses/verifica-file-tsd]] — caso specifico .tsd
- [[entities/signeddocumentvalidator]] — entry point validazione
- [[concepts/signature-validation]] — validazione firma in generale
