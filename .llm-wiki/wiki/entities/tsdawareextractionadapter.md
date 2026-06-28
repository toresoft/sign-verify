---
type: entity
category: project
created: 2026-06-28
updated: 2026-06-28
sources:
  - concepts/file-extraction
---

# TsdAwareExtractionAdapter

Decorator di `DssExtractionAdapter` ([[entities/dssextractionadapter]]) che aggiunge supporto per estrarre contenuto da file **RFC 5544 TimeStampedData** (`.tsd`).

## Perché serve

DSS 6.4 non ha factory per `id-aa-timeStampedData`: `SignedDocumentValidator.fromDocument()`
lancia `IllegalInputException` su file TSD. L'adapter prova l'unwrap via Bouncy Castle
**prima di** delegare a DSS, così il circuit breaker non vede mai i fallimenti di parse TSD.

## Flusso

```
POST /api/v1/extractions (file .tsd)
  → TsdAwareExtractionAdapter (@Primary, @CircuitBreaker("dssExtraction"))
    → tryUnwrapTsd() → CMSTimeStampedData (Bouncy Castle)
    → inner content → ExtractionResult(RFC5544_TSD, [ExtractedFile])
    → se non è TSD → delegate.extract() (DssExtractionAdapter, DSS nativo)
```

## Differenze vs TsdAwareValidatorAdapter

| | TsdAwareValidatorAdapter | TsdAwareExtractionAdapter |
|---|---|---|
| Endpoint | `POST /verifications` | `POST /extractions` |
| Porta | `SignatureValidatorPort` | `ExtractionPort` |
| Delegate | `DssValidatorAdapter` | `DssExtractionAdapter` |
| Circuit breaker | Sul delegate (`dssValidator`) | Sul decorator (`dssExtraction`) |
| TSD unwrap | Dopo fallimento delegate | Prima di chiamare delegate |
| Output | `ValidationResult` (indication, reports, timestamps) | `ExtractionResult` (inner content bytes) |

## Test

- `TsdExtractionSmokeTest` — API-level: POST `/extractions` con `.tsd` → 200 OK, inner content restituito
- `DssExtractionAdapterTest` — 2 test (PAdES extraction, unsigned PDF error)

## Related

- [[entities/tsdawarevalidatoradapter]] · [[entities/dssextractionadapter]] · [[entities/dss]]
- [[concepts/file-extraction]] · [[concepts/rfc5544-tsd]]
- [[analyses/verifica-file-tsd]]
