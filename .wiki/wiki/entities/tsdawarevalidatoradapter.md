---
type: entity
category: project
created: 2026-06-28
updated: 2026-06-28
sources:
  - analyses/verifica-file-tsd
volatility: warm
---

# TsdAwareValidatorAdapter

Decorator di `DssValidatorAdapter` ([[entities/dssvalidatoradapter]]) che aggiunge supporto per file
**RFC 5544 TimeStampedData** (`.tsd`, OID `1.2.840.113549.1.9.16.1.31`).

## Perché serve

DSS 6.4 non ha factory per `id-aa-timeStampedData`: `SignedDocumentValidator.fromDocument()`
lancia `IllegalInputException` su file TSD. L'adapter intercetta l'eccezione e tenta l'unwrap
via **Bouncy Castle** (`CMSTimeStampedData`, già nel classpath via `bcpkix 1.84`).

## Flusso

```
POST /api/v1/verifications (file .tsd)
  → TsdAwareValidatorAdapter
    → try: SignedDocumentValidator.fromDocument(doc)  ← DSS nativo
    → catch IllegalInputException:
        → CMSTimeStampedData.parse(tsdBytes)
        → inner = tsd.getContent()                     ← inner .p7m / content
        → tokens = tsd.getTimeStampTokenEvidence()     ← RFC 3161 wrapper tokens
        → SignedDocumentValidator.fromDocument(inner)
        → DetachedTimestampValidator per ogni token
    → aggrega esito (strict: INDETERMINATE se token wrapper indeterminato)
```

## Test

- [[entities/tsdawarevalidatoradapter|TsdAwareValidatorAdapterTest]] — 4 test (adapter type, validazione, garbage, timestamps)
- `TsdSmokeTest` — API-level: POST `/verifications` con `.tsd` → 200 OK
- [[entities/rfc5544tsdroutingtest|Rfc5544TsdRoutingTest]] — test routing DSS (conferma rifiuto nativo)

## Related

- [[entities/dssvalidatoradapter]] · [[entities/dss]] · [[concepts/rfc5544-tsd]]
- [[analyses/verifica-file-tsd]] · [[outputs/plan-verifica-file-tsd-2026-06-28]]
- [[entities/tsdawareextractionadapter]] (extraction counterpart)
- [[concepts/baseline-profiles]] · [[concepts/timestamping]]
