---
type: analysis
created: 2026-06-28
updated: 2026-06-28
query: "quali file .p7m e PDF firmati usare per i test funzionali di sign-verify-2"
sources:
  - sources/signed-files-test-corpus-research
---

# Corpus CAdES/PAdES per test funzionali — catalogo & selezione

_Risposta: quali fixture firmati committare e come scaricarli._

## Raccomandazione

Fonte primaria: **DSS `validation/`** (PAdES + CAdES) — stessa libreria (DSS 6.4), comportamento
autoritativo, raw-fetchable (200 verificato), LGPL-2.1. Integrare con **SiVa-Test** (PAdES EU eID) e
**digidoc4j** (ASiC + migliori negativi). Per il caso PA italiano (nessun corpus pubblico): generare
con `openssl cms -cades` / DSS.

## Selezione minima consigliata (fixture da committare)

PAdES (`src/test/resources/signatures/pades/`):
| File | Caso |
|---|---|
| `pades-bes.pdf` | B valido |
| `PAdES-LT.pdf` | LT valido |
| `PAdES-LTA.pdf` | LTA valido (con archive-timestamp) |
| `pades-5-signatures-and-1-document-timestamp.pdf` | multi-firma + doc timestamp |
| `modified_after_signature.pdf` | negativo: modificato dopo la firma |
| `pdf-signed-corrupted.pdf` | negativo: firma corrotta |

CAdES (`src/test/resources/signatures/cades/`):
| File | Caso |
|---|---|
| `cades-bes-signeddata-enveloping.p7m` | B enveloping (stile `.p7m` PA) |
| `Signature-CBp-LT-2.p7m` | LT |
| `Signature-C-B-LTA-10.p7m` | LTA |
| `cades-bes-signeddata-detached.p7s` (+ originale) | detached |
| `malformed-cades.p7m` | negativo: malformato |
| `cades-broken-sig-tst.p7m` | negativo: timestamp rotto |

## Script di download (raw, no auth)

```bash
BASE=https://raw.githubusercontent.com/esig/dss/master
P=src/test/resources/signatures
mkdir -p $P/pades $P/cades
for f in pades-bes.pdf PAdES-LT.pdf PAdES-LTA.pdf \
         pades-5-signatures-and-1-document-timestamp.pdf \
         modified_after_signature.pdf pdf-signed-corrupted.pdf; do
  curl -fSL "$BASE/dss-pades/src/test/resources/validation/$f" -o "$P/pades/$f"
done
for f in cades-bes-signeddata-enveloping.p7m Signature-CBp-LT-2.p7m \
         Signature-C-B-LTA-10.p7m cades-bes-signeddata-detached.p7s \
         malformed-cades.p7m cades-broken-sig-tst.p7m; do
  curl -fSL "$BASE/dss-cades/src/test/resources/validation/$f" -o "$P/cades/$f"
done
```

## Avvertenze

- **Trust al validation-time**: i fixture DSS incatenano a CA EU (LOTL) potenzialmente **scadute** →
  l'esito può essere `INDETERMINATE` per POE mancanti, non `PASSED`. Per asserzioni deterministiche su
  `PASSED` servono o (a) policy/validation-time fissati, o (b) fixture generati con una CA di test propria
  (pyHanko / DSS `CAdESService` / `PAdESService`). Vedi [[concepts/validation-profiles]], [[concepts/etsi-en-319-102-1-validation]].
- **`.p7s` detached**: richiedono il documento originale per validare — scaricare anche quello.
- **Licenze**: DSS/digidoc4j LGPL-2.1; SiVa-Test EUPL-1.1. Aggiungere README di attribuzione accanto ai fixture.
- **Caso PA italiano**: generare `.p7m` con `openssl cms -cades -nodetach` (chain di test) — nessun corpus pubblico.

## Mappatura ai casi di verifica

- Positivi B/T/LT/LTA → asserire `signatureFormat`, `indication`, `bestSignatureTime` ([[analyses/tsd-dto-mapping]]).
- Multi-firma → `signatureCount > 1`.
- Negativi (modified/corrupted/malformed/broken-tst) → `FAILED`/`INDETERMINATE` con subIndication attesa.

## Fonti
- Ricerca corpus — [[sources/signed-files-test-corpus-research]]

## Related
- [[analyses/tsd-test-corpus]] · [[analyses/verifica-file-tsd]]
- [[concepts/ades-signature-formats]] · [[concepts/baseline-profiles]] · [[concepts/signature-validation]]
- [[concepts/validation-profiles]] · [[concepts/reports]] · [[concepts/trusted-lists]]
