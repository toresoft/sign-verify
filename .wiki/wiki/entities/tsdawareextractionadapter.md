---
type: entity
category: project
created: 2026-06-28
updated: 2026-07-01
verified: 2026-07-01
sources:
  - raw/notes/2026-07-01-ll-extraction-recursive-unwrap
  - concepts/file-extraction
volatility: warm
confidence: high
aliases: [RecursiveExtractionAdapter]
---

# TsdAwareExtractionAdapter → RecursiveExtractionAdapter (rinominato)

> **Rinominato.** Dalla feature `feat/extraction-recursive-unwrap` (2026-07-01) questa classe si chiama
> **`RecursiveExtractionAdapter`** ed è stata generalizzata da decorator solo-TSD a driver di
> sbustamento ricorsivo per qualsiasi container firmato annidato.
> **Voce canonica:** [[entities/recursiveextractionadapter]].

## Cosa è cambiato

- **Nome:** `TsdAwareExtractionAdapter` → `RecursiveExtractionAdapter`.
- **Ruolo:** prima sbustava un solo livello TSD; ora ricorre (TSD → p7m/CAdES → … → leaf) con
  `MAX_DEPTH=10` e distinzione leaf (depth>0) vs errore (depth==0).
- **Filename:** ora opzionale (dedotto via `ContentTypeDetector`).
- Resta `@Primary`, `@CircuitBreaker("dssExtraction")` sulla sola entry pubblica; l'unwrap TSD via
  BouncyCastle resta il primo passo prima di delegare a [[entities/dssextractionadapter]].

Dettagli completi nella voce canonica [[entities/recursiveextractionadapter]].

## Related

- [[entities/recursiveextractionadapter]] · [[entities/tsdawarevalidatoradapter]] · [[entities/dssextractionadapter]]
- [[concepts/file-extraction]] · [[concepts/rfc5544-tsd]]
- [[2026-07-01-ll-extraction-recursive-unwrap]]
