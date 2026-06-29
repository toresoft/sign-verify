---
type: analysis
created: 2026-06-27
updated: 2026-06-27
query: "come mappare DSS Reports in DTO REST per file .tsd"
sources:
  - sources/dss-timestamp-api-research
  - sources/etsi-en-319-102-1-timestamp
volatility: warm
---

# TSD Verification — Mapping DSS Reports → DTO REST

_Risposta di ricerca alla domanda: «Come esporre nel response JSON tutte le informazioni di firma e marca temporale per file .tsd?»_

## Struttura DTO raccomandata

Riferimento: SiVa (Estonian Signature Validation Service, open-source, DSS-based).

```json
{
  "validationTime": "2026-06-27T12:00:00Z",
  "signatureForm": "CAdES",
  "signaturesCount": 1,
  "validSignaturesCount": 1,
  "overallIndication": "TOTAL_PASSED",
  "signatures": [
    {
      "id": "...",
      "indication": "TOTAL_PASSED",
      "subIndication": null,
      "signatureFormat": "CAdES_BASELINE_LT",
      "signatureLevel": "QESIG",
      "signedBy": "Mario Rossi (CN)",
      "claimedSigningTime": "2026-06-27T10:00:00Z",
      "bestSignatureTime": "2026-06-27T10:05:00Z",
      "signatureScopes": [...],
      "timestamps": [
        {
          "id": "...",
          "type": "SIGNATURE_TIMESTAMP",
          "productionTime": "2026-06-27T10:05:00Z",
          "producedBy": "Aruba PEC TSA",
          "indication": "TOTAL_PASSED",
          "subIndication": null,
          "qualification": "QTSA"
        }
      ],
      "counterSignatures": [],
      "certificates": [...]
    }
  ]
}
```

## Mapping campo per campo

| Campo DTO | DSS API | Note |
|---|---|---|
| `validationTime` | `new Date()` | Ora validazione server |
| `signaturesCount` | `sr.getSignaturesCount()` | Solo master |
| `validSignaturesCount` | `sr.getValidSignaturesCount()` | — |
| `overallIndication` | derive da `validSignaturesCount == signaturesCount` | TOTAL_PASSED / FAILED / INDETERMINATE |
| `signature.id` | `SignatureWrapper.getId()` | — |
| `signature.indication` | `sr.getIndication(sigId)` | — |
| `signature.subIndication` | `sr.getSubIndication(sigId)` | null se PASSED |
| `signature.signatureFormat` | `sr.getSignatureFormat(sigId).name()` | es. "CAdES_BASELINE_LT" |
| `signature.signatureLevel` | `sr.getSignatureLevel(sigId)` | QESIG / QESEAL / ... |
| `signature.signedBy` | `sr.getSignedBy(sigId)` | CN del firmatario |
| `signature.claimedSigningTime` | `sr.getSigningTime(sigId)` | Dichiarato dal firmatario |
| `signature.bestSignatureTime` | `sr.getBestSignatureTime(sigId)` | **Preferire questo** per scopi legali |
| `timestamp.id` | `TimestampWrapper.getId()` | — |
| `timestamp.type` | `ts.getType().name()` | SIGNATURE_TIMESTAMP / ARCHIVE_TIMESTAMP |
| `timestamp.productionTime` | `sr.getProductionTime(tsId)` | Ora TSA — campo principale |
| `timestamp.producedBy` | `sr.getProducedBy(tsId)` | Nome TSA da TrustedList; può essere null |
| `timestamp.indication` | `sr.getIndication(tsId)` | — |
| `timestamp.subIndication` | `sr.getSubIndication(tsId)` | null se PASSED |
| `timestamp.qualification` | `sr.getTimestampQualification(tsId)` | QTSA / TSA / NA |
| `timestamp.algorithm` | `dd.getTimestampById(tsId).getDigestAlgorithm()` | Non in SimpleReport |

## Codice Java per iterazione

```java
Reports reports = validator.validateDocument();
DiagnosticData dd = reports.getDiagnosticData();
SimpleReport sr = reports.getSimpleReport();

List<SignatureDto> signatureDtos = new ArrayList<>();

for (SignatureWrapper sig : dd.getAllSignatures()) {  // getAllSignatures() esclude counter-sig
    List<TimestampDto> tsDtos = sig.getTimestampList().stream()
        .map(ts -> TimestampDto.builder()
            .id(ts.getId())
            .type(ts.getType().name())
            .productionTime(sr.getProductionTime(ts.getId()))
            .producedBy(sr.getProducedBy(ts.getId()))
            .indication(sr.getIndication(ts.getId()).name())
            .subIndication(nullSafe(sr.getSubIndication(ts.getId())))
            .qualification(nullSafe(sr.getTimestampQualification(ts.getId())))
            .build())
        .toList();

    List<SignatureDto> counterDtos = dd.getAllCounterSignaturesForMasterSignature(sig)
        .stream()
        .map(cs -> mapSignature(cs, dd, sr))  // ricorsione
        .toList();

    signatureDtos.add(SignatureDto.builder()
        .id(sig.getId())
        .indication(sr.getIndication(sig.getId()).name())
        .subIndication(nullSafe(sr.getSubIndication(sig.getId())))
        .signatureFormat(sr.getSignatureFormat(sig.getId()).name())
        .signedBy(sr.getSignedBy(sig.getId()))
        .claimedSigningTime(sr.getSigningTime(sig.getId()))
        .bestSignatureTime(sr.getBestSignatureTime(sig.getId()))
        .timestamps(tsDtos)
        .counterSignatures(counterDtos)
        .build());
}
```

## Semantica aggregazione INDETERMINATE

`INDETERMINATE` NON è `FAILED` — significa "dati di validazione incompleti per una determinazione definitiva". Scenari comuni per TSD in PA:
- TSA non in TrustedList → `INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` sulla marca; firma B-level può rimanere `PASSED`
- TSA scaduto senza POE → `INDETERMINATE / OUT_OF_BOUNDS_NO_POE`

La policy di aggregazione va dichiarata nell'OpenAPI spec. Opzioni:
- **Strict**: qualsiasi INDETERMINATE su marca → overall INDETERMINATE
- **Lenient**: solo FAILED su firma master → overall FAILED; INDETERMINATE su marca = warning

## Test plan

1. File `.tsd` CAdES-T (da ArubaSign/GoSign) → asserire firma PASSED + timestamp PASSED, `bestSignatureTime` popolato
2. TSA non in TrustedList → `INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` su timestamp; firma B-level PASSED
3. Multi-firma (2 firmatari) → `signaturesCount=2`, 2 entries in `signatures[]`
4. Counter-firma → `signatures[]` mostra master + entry `counterSignatures[]` separata

## Related

- [[concepts/dss-timestamp-api]] — API DSS di riferimento
- [[concepts/etsi-en-319-102-1-validation]] — semantica normativa indication/subIndication
- [[concepts/cades-counter-signatures]] — gestione counter-firme
- [[analyses/verifica-file-tsd]] — routing validazione .tsd
- [[concepts/design-first-openapi]] — OpenAPI spec dove va dichiarata la semantica
