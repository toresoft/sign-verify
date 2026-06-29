---
type: synthesis
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
  - sources/SRC-2026-06-27-008
volatility: warm
---

# Validation at a specific date (control time)

_Synthesis answering: «esiste la possibilità di impostare una data entro cui forzare la validazione?»_

## Risposta sintetica
- **In [[entities/sign-verify-2]] oggi: NO.** L'API non espone un parametro "data di validazione". Il `metadata` JSON di `POST /api/v1/verifications` (e `/async`) porta solo `profileId?`, `profileOverrides?`, `reports[]?` (+ `callbackUrl/Secret/Algorithm` per async) — vedi [[sources/SRC-2026-06-27-003]] §4.3/4.4 e lo schema ([[sources/SRC-2026-06-27-008]]). La validazione gira al **tempo corrente** (`verifiedAt` = `now`). Scansione del contratto OpenAPI per campi `date`/`time` → nessun risultato.
- **In [[entities/dss]]: SÌ.** La libreria accetta un `validationDate` ("Define validation time (optional, if not defined the current time will be used)", §13.1.4) e supporta la **validazione nel passato** (PCV, VTS, estrazione POE, PSV) quando la validazione base al current-time finisce `INDETERMINATE` e i Proof-Of-Existece embedded aiutano a raggiungere uno stato determinato ([[sources/SRC-2026-06-27-001]] §7).

## La semantica eIDAS (importante, anti-malusupposizione)
Non si "forza la validità a una data X" in modo arbitrario. Il modello ETSI EN 319 102-1:
- valida al **tempo corrente**, ma usa i **POE** (timestamp embedded) come prove d'esistenza per estendere la validità nel passato. Il **best-signature-time** è inizializzato da un POE correlato o, in assenza, dal tempo corrente.
- Il **control time** (a cui si valutano `NotExpired`, `RevocationIssuerNotExpired`, ecc.) segue il `<Model>` **SHELL/CHAIN/HYBRID** (§20.2.2.8) — SHELL = relativo al control time; CHAIN = relativo al tempo di emissione del figlio.
- La qualifica del certificato è calcolata a **due tempi** (emissione + firma/validazione) per eIDAS Art. 32 — vedi [[concepts/signature-validation]], [[entities/eidas-regulation]].

Pertanto "impostare una data" significa fissare il **control time / best-signature-time** a quel valore: DSS userà i POE ≤ quella data per stabilire lo stato. La freshness di revoca è relativa al best-signature-time / lowest POE (`RevocationDataVerifier.set*MaximumRevocationFreshness`).

## Come si aggiungerebbe a sign-verify-2 (gaps attuali)
1. **Contratto:** aggiungere a `metadata` un campo facoltativo `validationDate` (ISO-8601) nello schema di [[concepts/design-first-openapi|OpenAPI]] (design-first → `OpenApiContractIT` forza la rigenerazione). Estensione pulita del meccanismo di [[concepts/validation-profiles|profileOverrides]].
2. **Adapter:** in [[entities/dssvalidatoradapter|DssValidatorAdapter]], raccogliere `validationDate` e impostare il tempo di validazione sul `SignedDocumentValidator`/`CertificateVerifier` (DSS espone il setter interno; `validationDate` del §13.1.4 è il riferimento API).
3. **Risultato:** `verifiedAt` dovrebbe riflettere la data impostata (non `now`); esporre nel [[concepts/reports|Reports]] che la validazione è stata eseguita "as-of". Decidere la **semantica di aggregazione** coerente con [[concepts/signature-validation]] (signature B-level vs marca TSA) — stesso caveat del caso `.tsd` ([[analyses/verifica-file-tsd]]).

## Gap di conoscenza
Il design spec ([[sources/SRC-2026-06-27-002]]) e i docs operativi **non menzionano** esplicitamente una feature "validation-at-a-date". Per chiuderla servirebbe:
- catturare lo **schema OpenAPI reale di `VerificationRequest.metadata`** (confermare l'assenza del campo), e/o
- una **decisione di design** (ADR) se la si vuole introdurre (modello SHELL di default? binding a `best-signature-time`?).

## Related
- [[concepts/signature-validation]] · [[concepts/baseline-profiles]] · [[concepts/timestamping]]
- [[concepts/validation-profiles]] · [[entities/dssvalidatoradapter]] · [[concepts/design-first-openapi]]
- [[entities/eidas-regulation]] · [[analyses/verifica-file-tsd]]