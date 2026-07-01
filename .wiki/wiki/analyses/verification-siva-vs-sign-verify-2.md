---
type: analysis
category: comparison
created: 2026-07-01
updated: 2026-07-01
query: "differenze di implementazione per la verifica firma tra sign-verify-2 e SiVa"
sources:
  - sources/SRC-2026-06-28-001
  - https://open-eid.github.io/SiVa/siva3/interfaces/
  - https://open-eid.github.io/SiVa/siva2/appendix/validation_policy/
tags: [siva, verification, validation, dss, policy, hashcode, signed-report, async, comparison]
confidence: high
volatility: warm
summary: "Verifica firma: entrambi eIDAS su EU DSS. SiVa usa un FORK org.digidoc4j.dss, monolite modulare, sync-only (/validate + /validateHashcode), policy fissa POLv3/POLv4, hashcode-mode e report firmato ASiC-E, auth via X-Road. sign-verify-2 usa DSS 6.4 upstream, architettura esagonale, sync + async job + webhook HMAC, preset BASIC/STANDARD/STRICT + AGID + policy override, TSL hot-swap, RFC 5544 TSD, errori problem+json, circuit breaker. I gap residui di sign-verify-2 verso SiVa: hashcode-mode e report firmato."
---

# Verifica firma — SiVa vs sign-verify-2

Confronto focalizzato sul **percorso di verifica/validazione firma** tra
[[entities/siva|open-eid/SiVa]] e [[entities/sign-verify-2]]. Estende il confronto generale
[[analyses/siva-vs-sign-verify-2]] e il gemello [[analyses/extraction-siva-vs-sign-verify-2]]
(estrazione). Fatti SiVa ancorati a [[sources/SRC-2026-06-28-001]] e riconfermati 2026-07-01 su
<https://open-eid.github.io/SiVa/siva3/interfaces/> e
<https://open-eid.github.io/SiVa/siva2/appendix/validation_policy/>. Fatti sign-verify-2 da
[[entities/dssvalidatoradapter]], [[concepts/validation-profiles]], [[concepts/reports]].

Entrambi sono servizi **solo-validazione** eIDAS costruiti su **EU DSS** — SiVa è il peer pubblico
più vicino. Le differenze sono di *pipeline*, non di algoritmo di validazione (che in ultima analisi
è DSS + ETSI EN 319 102-1 in entrambi).

## Quadro sintetico (verifica)

| Dimensione | SiVa | sign-verify-2 |
|---|---|---|
| Libreria DSS | **Fork** `org.digidoc4j.dss` (lag upstream; DSS 6.x pianificato 3.11 Dic 2026) | **Upstream DSS 6.4** ✅ |
| Architettura pipeline | Monolite modulare: gateway → proxy → validatore per-formato | Esagonale (porte/adapter), ArchUnit ✅ |
| Modalità API | **Sync-only**: `/validate`, `/validateHashcode` | Sync **+ job async + webhook HMAC** ✅ |
| Policy | **Fissa**: `POLv3` (tutti i livelli) / `POLv4` (QES-only, default) | Preset `BASIC`/`STANDARD`/`STRICT` + **AGID/AGID_TS** (QES-strict IT) + **policy override** per-richiesta |
| Semantica QES vs AdES | POLv4 rifiuta AdES/AdES-QC dal risultato positivo | `signatureLevel` esposto ([[concepts/signature-qualification]]); preset AGID replicano POLv4-like |
| Hashcode / `DigestDocument` mode | **Sì** (datafile+hash+algo, oppure solo file firma) | **No** (backlog — punto 4 di [[analyses/siva-vs-sign-verify-2]]) ❌ |
| Report firmato | **Sì — ASiC-E** (livello configurabile, Base64 accanto al report; prova autenticità/integrità dell'autorità) | **No** (backlog — punto 3) ❌ |
| Tier di report | `Simple` / `Detailed` / `Diagnostic` | `simple` / `detailed` / `diagnostic` / **`etsi`** ✅ ([[concepts/reports]]) |
| Timestamp nel report | `timeStampTokens[]`, `archiveTimeStamps[]`, livello QTSA/TSA (più ricco) | `signatures[].timestamps[]` con livello; manca `archiveTimeStamps[]` separato ⚠️ |
| Formati verificabili | DDOC/BDOC (legacy EE), PAdES, ASiC-E/S, XAdES-hashcode (+ JAdES 3.11) | PAdES/CAdES/XAdES/**JAdES**/ASiC **+ RFC 5544 TSD** ✅ ([[concepts/rfc5544-tsd]]) |
| Trusted Lists | EU LOTL con pivot, refresh cron, cache online/offline; trust EE statico per DDOC | EU LOTL, **hot-swap ShedLock** senza restart ([[concepts/tsl-hot-swap-refresh]]) ✅ |
| Auth | Delegata a **X-Road** | **API-key + OAuth2 JWT** ✅ |
| Errori | Custom | **`application/problem+json`** (RFC 9457) ✅ |
| Resilienza | — | **Circuit breaker** + backpressure (429) ✅ |

## Differenze di fondo

- **Stesso motore, fork diverso.** Il cuore di validazione è DSS in entrambi. SiVa lo consuma via
  **fork** `org.digidoc4j.dss` (per supportare i formati legacy estoni e la loro cadenza di rilascio),
  pagando il ritardo sugli aggiornamenti upstream (transizione Trusted List v6). sign-verify-2 sta su
  **upstream DSS 6.4** — nessun fork-lag. Questo è il singolo delta architetturale più importante.

- **Sync-only vs async-first.** SiVa espone solo chiamate sincrone. sign-verify-2 aggiunge job
  asincroni + webhook HMAC, superiore per carichi batch — con circuit breaker e backpressure a
  proteggere il DSS (validazione costosa, dipendente da rete: OCSP/CRL/AIA, [[concepts/trusted-lists]]).

- **Policy fissa vs preset + override.** SiVa offre due policy predefinite (POLv3 permissiva, POLv4
  QES-only di default). sign-verify-2 offre preset graduati (BASIC/STANDARD/STRICT) più i preset
  **AGID** italiani (QES-strict, allineati alle Regole Tecniche AgID) e un meccanismo di **override**
  di policy per-richiesta — più flessibile per un integratore PA.

- **Verifica per hash (hashcode) e report firmato: qui SiVa è avanti.** Sono i due gap reali di
  sign-verify-2 sul percorso di verifica (entrambi già a backlog):
  - *Hashcode mode* — validare per `hashAlgo`+`hash` senza il file originale (privacy/banda). DSS
    supporta `DigestDocument`; la logica è affine al resolver imprint già scritto per il
    [[concepts/rfc5544-tsd|TSD]].
  - *Report firmato* — SiVa restituisce il report in un **ASiC-E firmato** → non-ripudio del
    verdetto. Differenziatore forte per una PA. Richiede un `ReportSignerPort` + adapter DSS.

- **TSD RFC 5544: qui sign-verify-2 è avanti.** sign-verify-2 verifica (e ora estrae ricorsivamente)
  i TimeStampedData RFC 5544 via BouncyCastle davanti a DSS; SiVa non li tratta.

## Dove sign-verify-2 è già alla pari o avanti
Upstream DSS, async+webhook, auth applicativa, esagonale+ArchUnit, TSL hot-swap, `problem+json`,
circuit breaker, `signatureLevel` esposto, tier `etsi`, JAdES nativo, TSD RFC 5544. Vedi
[[analyses/siva-vs-sign-verify-2]] per l'elenco completo e i punti di miglioramento.

## Anti-pattern SiVa da NON copiare (verifica)
- **Fork DSS** — coupling e ritardo upstream.
- **Sync-only** — batch penalizzato.
- **Trust estone hardcoded** per DDOC — non neutrale eIDAS, irrilevante per la PA IT.
- **Un Tomcat per servizio** (conseguenza del multi-libreria) — evitato dall'architettura a servizio
  singolo esagonale.

## Related
- [[analyses/siva-vs-sign-verify-2]] · [[analyses/extraction-siva-vs-sign-verify-2]] · [[analyses/architecture-siva-vs-sign-verify-2]]
- [[concepts/siva-validation-policy]] · [[concepts/siva-report-schema]] · [[concepts/siva-rest-interface]]
- [[concepts/validation-profiles]] · [[concepts/reports]] · [[concepts/signature-qualification]]
- [[concepts/etsi-en-319-102-1-validation]] · [[concepts/trusted-lists]] · [[concepts/rfc5544-tsd]]
- [[entities/siva]] · [[entities/sign-verify-2]] · [[entities/dss]] · [[entities/dssvalidatoradapter]]
