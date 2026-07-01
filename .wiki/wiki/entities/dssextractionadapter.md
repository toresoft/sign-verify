---
type: entity
category: project
created: 2026-06-27
updated: 2026-07-01
verified: 2026-07-01
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-006
  - raw/notes/2026-07-01-ll-extraction-recursive-unwrap
volatility: warm
confidence: high
---

# DssExtractionAdapter

The [[entities/sign-verify-2]] adapter implementing the `ExtractionPort` ([[concepts/hexagonal-architecture]]). It uses [[entities/dss]] to locate the original unsigned document(s) embedded in a signed file and infer the signature format, powering [[concepts/file-extraction|File extraction]] (`POST /api/v1/extractions`).

È l'estrazione **single-level** (un giro di `getOriginalDocuments`); il driver ricorsivo che lo avvolge è [[entities/recursiveextractionadapter]].

Single original → binary response with its MIME type; multiple originals (typical of ASiC-E) → ZIP. Sets `X-Signature-Format` and `X-Document-Count` headers.

## Comportamento (feature `feat/extraction-recursive-unwrap`, 2026-07-01)

- **Filename dedotto:** se il filename è null/blank, costruisce `document<ext>` via
  `ContentTypeDetector` (magic-byte) e lo passa a `InMemoryDocument`, preservando gli hint di formato
  DSS. Gli original senza nome ricevono lo stesso naming/mime dedotto.
- **Formato reale:** `signatureFormat()` = `signatures.get(0).getSignatureForm().name()`
  (PAdES/CAdES/XAdES/JAdES/PKCS7), non più la costante `"UNKNOWN"` (fallback solo se non ricavabile).
- **Tutte le chiamate DSS tradotte in `AppException`:** `fromDocument()`, `getSignatures()`
  (`getSignaturesOrThrow`) e il loop di lettura originali. Necessario perché
  [[entities/recursiveextractionadapter]] ri-alimenta gli original e ogni eccezione grezza (es.
  `DigestDocument.openStream()` → `UnsupportedOperationException`) avvelenerebbe il
  [[concepts/circuit-breaker|circuit breaker]] `dssExtraction`. Vedi L1 in
  [[2026-07-01-ll-extraction-recursive-unwrap]].

## Related
- [[concepts/file-extraction]] · [[entities/dss]] · [[entities/recursiveextractionadapter]]
- [[concepts/hexagonal-architecture]] · [[concepts/ades-signature-formats]] · [[concepts/circuit-breaker]]
- [[2026-07-01-ll-extraction-recursive-unwrap]]
