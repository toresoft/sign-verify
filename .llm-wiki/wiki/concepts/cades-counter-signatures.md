---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/dss-counter-signature-research
---

# CAdES Counter-Signatures in DSS 6.4

## Cosa sono

Una counter-firma CAdES firma i **bytes della firma del firmatario master** (il campo `signature` dentro `SignerInfo`), non il documento originale. Usata per attestare che il controfimatario ha visto e accettato la firma del firmatario.

## Supporto DSS — limiti importanti

- **Profondità max = 1**: counter-firma di una counter-firma lancia `UnsupportedOperationException("Nested counter signatures are not supported with CAdES!")` — solo a tempo di firma, non a tempo di validazione di file provenienti da altri tool.
- **Livello counter-firma = solo BASELINE-B**: `UnsupportedOperationException("A counter signature with a level 'CAdES-BASELINE-LTA' is not supported!")`
- **B-level reference check**: `checkBLevelValid()` è deliberatamente vuoto (svuotato nei test DSS con commento "counter signature reference fails") — il check di riferimento B-level sui counter non viene eseguito.
- **`verifyOriginalDocuments()`**: svuotato per counter-firme (firmano bytes della firma, non il documento).

## API DSS: iterazione firme (trappola conteggio)

```java
// ⚠️ getSignatures() INCLUDE counter-firme → conta errata
diagnosticData.getSignatures().size()      // SBAGLIATO per "quanti firmatari"

// CORRETTI:
diagnosticData.getAllSignatures()           // solo master (parent == null)
diagnosticData.getAllCounterSignatures()    // solo counter (parent != null)
diagnosticData.getAllCounterSignaturesForMasterSignature(masterWrapper)

signatureWrapper.isCounterSignature()       // flag discriminatore
signatureWrapper.getParent()                // master della counter-firma (o null se master)
```

## Comportamento validateDocument()

- `validateDocument()` processa TUTTE le counter-firme trovate nel CMS SignerInformation tree.
- Compaiono in `DiagnosticData.getSignatures()` con `isCounterSignature() == true`.
- Ogni counter-firma ha una validazione **indipendente** nel SimpleReport (separate entries).
- Un master TOTAL_PASSED NON implica counter-firma TOTAL_PASSED (viceversa pure).

## INDETERMINATE per counter-firme B-level

Una counter-firma BASELINE-B **non ha timestamp né revocation data propri**. DSS riusa il CMS certificate/revocation store del master per la chain building, ma:
- La **qualification** del certificato del controfimatario dipende dalla prova di esistenza (POE) fornita dai timestamp del master.
- Se il master ha un archive timestamp (LTA) che copre anche il periodo di validità del certificato del controfimatario → chain può essere risolta.
- Senza questo, il counter-signer certificate può risultare `INDETERMINATE / NO_POE` o `OUT_OF_BOUNDS_NO_POE`.

## Scenario multi-firma parallela (comune in .tsd PA)

Due firmatari sullo stesso documento → due entry in `getAllSignatures()`, validate indipendentemente. Non ci sono interazioni tra firme parallele.

```java
// 2 firmatari + 2 counter-firme (una per ciascun firmatario):
dd.getSignatures().size()       // 4 (2 master + 2 counter)
dd.getAllSignatures().size()     // 2 (solo master)
dd.getAllCounterSignatures()     // 2 (solo counter)
```

## Bug risolti in DSS 6.x rilevanti

| Issue | Fix | Effetto |
|---|---|---|
| DSS-3705 | DSS 6.3 | Counter-firma senza `Reference Type` attr era silenziosa in validazione |
| DSS-3725 | DSS 6.4 | XAdES/JAdES counter-firme validate più volte (ridondante) |

**File `.tsd` prodotti da tool pre-6.3 potrebbero avere counter-firme con `Reference Type` mancante** — in DSS 6.4 queste sono ora rilevate correttamente.

## Related

- [[concepts/dss-timestamp-api]] — come leggere le indication per signature e counter-firma
- [[analyses/verifica-file-tsd]] — routing e validazione file .tsd
- [[entities/signeddocumentvalidator]] — validateDocument() entry point
