---
type: analysis
category: comparison
created: 2026-07-01
updated: 2026-07-01
query: "differenze di implementazione per l'estrazione del file originale tra sign-verify-2 e SiVa"
sources:
  - sources/SRC-2026-06-28-001
  - https://open-eid.github.io/SiVa/siva3/interfaces/
  - raw/notes/2026-07-01-ll-extraction-recursive-unwrap
tags: [siva, extraction, getdatafiles, ddoc, dss, rfc5544-tsd, recursion, comparison]
confidence: high
volatility: warm
summary: "SiVa estrae SOLO da container DDOC (legacy estone) via JDigiDoc, single-level, 'as is', 400 per ogni altro formato. sign-verify-2 estrae da qualsiasi container DSS (PAdES/CAdES/XAdES/JAdES/ASiC) + TSD RFC 5544, ricorsivamente fino al leaf, con filename deducibile. Sono progetti con scopi di estrazione diversi: SiVa espone getDataFiles come utility DDOC accessoria alla validazione; sign-verify-2 tratta l'estrazione come feature di primo livello e generale."
---

# Estrazione del file originale — SiVa vs sign-verify-2

Confronto focalizzato sull'**estrazione del contenuto originale** da un file firmato, tra
[[entities/siva|open-eid/SiVa]] e [[entities/sign-verify-2]]. Estende [[analyses/siva-vs-sign-verify-2]]
(confronto generale). Fatti SiVa ancorati a [[sources/SRC-2026-06-28-001]] (`docs/siva3/interfaces.md`,
commit `348a6b2`) e riconfermati il 2026-07-01 su <https://open-eid.github.io/SiVa/siva3/interfaces/>.
Fatti sign-verify-2 dalla feature `feat/extraction-recursive-unwrap` (vedi
[[concepts/file-extraction]], [[2026-07-01-ll-extraction-recursive-unwrap]]).

## Quadro sintetico

| Dimensione | SiVa `/getDataFiles` | sign-verify-2 `/api/v1/extractions` |
|---|---|---|
| Scopo | Utility accessoria alla validazione | Feature di primo livello |
| Formati sorgente | **Solo DDOC** (legacy estone) | **Qualsiasi container DSS** (PAdES/CAdES/XAdES/JAdES/ASiC) **+ RFC 5544 TSD** |
| Non-DDOC / altri formati | **400** — "Invalid document type. Can only return data files for DDOC type containers." | Estratti normalmente |
| Motore di estrazione | **JDigiDoc** (libreria legacy DDOC) | **EU DSS** `getOriginalDocuments()` + BouncyCastle per TSD |
| Profondità | **Single-level** (i datafile del DDOC, "as is") | **Ricorsiva** (TSD → p7m/CAdES → … → leaf), `MAX_DEPTH=10` |
| Container annidati | Non gestiti | Sbustati fino al file originale non firmato |
| Filename input | **Obbligatorio** (max 255) | **Opzionale** — dedotto via magic-byte ([[concepts/file-extraction|ContentTypeDetector]]) |
| Operazioni sul contenuto | **Nessuna** ("as is", né validazione né trasformazione) | Sniffing mime/nome per i leaf senza nome; nessuna alterazione del byte-stream |
| Forma risposta | JSON: array `dataFiles[]` con `fileName`, `size`, `base64`, `mimeType` | **Binario diretto** (single) o **ZIP** (multipli), header `X-Signature-Format`, `X-Document-Count` |
| Errori | Status + messaggio | **`application/problem+json`** (RFC 9457) |
| Resilienza | — | Circuit breaker `dssExtraction` (input errato → `AppException`, non apre il breaker) |
| Encoding I/O | Base64 in ingresso e uscita | Multipart in ingresso, binario in uscita |

## Differenza di fondo

Le due estrazioni rispondono a **problemi diversi**, non sono lo stesso feature implementato in
modo diverso:

- **SiVa** — l'estrazione è un residuo storico del formato **DDOC**. Un DDOC è un XML che
  incapsula i datafile in Base64; JDigiDoc li restituisce così come sono. Per tutti gli altri
  formati (BDOC, ASiC-E/S, PAdES…) SiVa **non estrae**: `/getDataFiles` risponde 400. La validazione
  SiVa supporta molti formati, ma l'estrazione no. È deliberatamente un'utility di nicchia per un
  formato legacy estone — irrilevante per la PA italiana.

- **sign-verify-2** — l'estrazione è una **feature generale e di primo livello**: da qualsiasi
  container eIDAS supportato da DSS ricava il contenuto firmato, e (novità della feature
  `feat/extraction-recursive-unwrap`) **sbuccia ricorsivamente** i container annidati — inclusi i
  TSD RFC 5544 che DSS non sa parsare da solo — fino al file originale non firmato.

## Dettaglio implementativo sign-verify-2

- **Motore:** `DssExtractionAdapter` fa un giro single-level di `SignedDocumentValidator.getOriginalDocuments(firstSigId)`; `RecursiveExtractionAdapter` (ex `TsdAwareExtractionAdapter`) è il driver che ri-alimenta ogni original nel delegate finché non raggiunge un leaf.
- **TSD:** BouncyCastle `CMSTimeStampedData` sbustato *prima* del delegate DSS (DSS 6.4 non ha factory per `id-aa-timeStampedData`). Vedi [[concepts/rfc5544-tsd]].
- **Terminazione:** parse-error a `depth==0` → 400 (input non firmato); a `depth>0` → leaf grezzo; bound `MAX_DEPTH=10`. `X-Signature-Format` riporta il formato del container **più esterno**.
- **Filename opzionale:** se il multipart non porta il filename, `ContentTypeDetector` (magic-byte) deduce `document<ext>`; il part `file` resta comunque obbligatorio (nessuna modifica OpenAPI).
- **Resilienza:** ogni chiamata DSS raggiungibile è tradotta in `AppException` così l'input errato non apre il circuit breaker condiviso — vedi [[2026-07-01-ll-extraction-recursive-unwrap]] L1.

## Cosa NON copiare da SiVa
- **Estrazione DDOC-only via JDigiDoc:** legata a un formato legacy estone e a una libreria fuori
  da DSS. sign-verify-2 fa bene a restare su DSS `getOriginalDocuments` (un solo motore, upstream).
- **400 su formati non-DDOC:** per sign-verify-2 sarebbe una regressione — l'estrazione generale è
  proprio il differenziatore.

## Cosa eventualmente prendere da SiVa
- **Hashcode/`DigestDocument` mode** (già a backlog in [[analyses/siva-vs-sign-verify-2]] punto 4):
  ortogonale all'estrazione ma tocca lo stesso `DigestDocument` che nel loop di lettura originali
  può lanciare `UnsupportedOperationException` — coperto ora dalla traduzione ad `AppException`.
- **`mimeType` + `size` tipizzati nella risposta:** oggi sign-verify-2 espone mime via header /
  entry ZIP; una risposta JSON strutturata (à la `dataFiles[]`) sarebbe utile per i client che
  vogliono metadati senza scaricare i byte. *(basso valore, valutare)*

## Related
- [[analyses/siva-vs-sign-verify-2]] · [[concepts/file-extraction]] · [[concepts/siva-rest-interface]]
- [[entities/siva]] · [[entities/sign-verify-2]] · [[entities/dss]]
- [[concepts/rfc5544-tsd]] · [[entities/dssextractionadapter]] · [[entities/tsdawareextractionadapter]]
- [[2026-07-01-ll-extraction-recursive-unwrap]]
