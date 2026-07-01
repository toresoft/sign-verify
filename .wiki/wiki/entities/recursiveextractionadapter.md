---
type: entity
category: project
created: 2026-07-01
updated: 2026-07-01
verified: 2026-07-01
sources:
  - raw/notes/2026-07-01-ll-extraction-recursive-unwrap
  - concepts/file-extraction
volatility: warm
confidence: high
---

# RecursiveExtractionAdapter

`@Primary` adapter dell'`ExtractionPort` ([[concepts/hexagonal-architecture]]) e **driver di
sbustamento ricorsivo** per `POST /api/v1/extractions`. Rinominato da `TsdAwareExtractionAdapter`
(vedi [[entities/tsdawareextractionadapter]]) e generalizzato: non più solo TSD, ma qualsiasi
container firmato annidato. Delega l'estrazione di un livello a [[entities/dssextractionadapter]].

## Cosa fa

Sbuccia ricorsivamente un file firmato fino al contenuto originale non firmato, gestendo container
**eterogenei annidati**: RFC 5544 TSD → p7m/CAdES → XAdES → ASiC → … → leaf.

- **Due motori in-process:** BouncyCastle per l'unwrap TSD ([[concepts/rfc5544-tsd]], che DSS 6.4 non
  parsa) + DSS `getOriginalDocuments()` per tutti gli altri container. Nessun Tomcat/servizio
  separato — vedi [[analyses/architecture-siva-vs-sign-verify-2]].
- **Integrazione via Decorator + try-fallback:** `tryUnwrapTsd(bytes)`; se è TSD ricorre sull'inner,
  altrimenti delega a DSS. Routing **content-attempt**, non basato sul `documentType` dichiarato.

## Algoritmo (`extractRecursive`, `MAX_DEPTH = 10`)

```
depth > MAX_DEPTH            → AppException.badRequest("...max depth...")  (loop guard)
tryUnwrapTsd(bytes) != null  → ricorre su inner (depth+1); a depth 0 formato = RFC5544_TSD
delegate.extract(bytes)      →
  AppException (parse fail)  → depth 0: propaga (400, input non firmato)
                               depth>0: LEAF grezzo, nome/mime da ContentTypeDetector
  success                    → per ogni original ricorre (depth+1)
```

- `X-Signature-Format` riporta il formato del container **più esterno** (`RFC5544_TSD` o forma DSS
  reale: PAdES/CAdES/XAdES/JAdES/PKCS7).
- `@CircuitBreaker("dssExtraction")` sulla **sola** entry pubblica `extract`; la ricorsione gira in un
  helper privato → non ri-entra nel proxy. Vedi [[concepts/circuit-breaker]] e la lezione L1 in
  [[2026-07-01-ll-extraction-recursive-unwrap]] (input errato tradotto in `AppException` per non
  avvelenare il breaker).

## Filename opzionale

Se il multipart non porta il filename, `ContentTypeDetector` (magic-byte: `%PDF`→pdf, `PK`→zip,
`<`→xml, DER SEQUENCE→p7m, default bin) deduce `document<ext>`. Il part `file` resta obbligatorio →
nessuna modifica al contratto OpenAPI ([[concepts/design-first-openapi]]).

## Differenze vs TsdAwareValidatorAdapter

| | [[entities/tsdawarevalidatoradapter]] | RecursiveExtractionAdapter |
|---|---|---|
| Endpoint | `POST /verifications` | `POST /extractions` |
| Porta | validazione | `ExtractionPort` |
| Delegate | `DssValidatorAdapter` | `DssExtractionAdapter` |
| Circuit breaker | sul delegate (`dssValidator`) | sul decorator (`dssExtraction`) |
| Ricorsione | no (unwrap singolo) | sì, `MAX_DEPTH=10` |

## Test

- `RecursiveExtractionAdapterTest` — TSD→leaf, TSD-in-TSD, TSD→container→leaf, propagazione depth 0,
  `MAX_DEPTH` superato → `AppException`.
- `TsdExtractionSmokeTest`, `ExtractionControllerIT` (filename omesso, TSD via HTTP).

## Related

- [[entities/dssextractionadapter]] · [[entities/tsdawarevalidatoradapter]] · [[entities/dss]]
- [[concepts/file-extraction]] · [[concepts/rfc5544-tsd]] · [[concepts/circuit-breaker]]
- [[analyses/extraction-siva-vs-sign-verify-2]] · [[analyses/architecture-siva-vs-sign-verify-2]]
- [[2026-07-01-ll-extraction-recursive-unwrap]]
