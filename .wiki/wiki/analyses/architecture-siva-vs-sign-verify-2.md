---
type: analysis
category: comparison
created: 2026-07-01
updated: 2026-07-01
query: "differenze di architettura, strutturazione del codice e pattern nella verifica firma tra sign-verify-2 e SiVa"
sources:
  - sources/SRC-2026-06-28-001
  - https://open-eid.github.io/SiVa/siva3/structure_and_activities/
  - https://open-eid.github.io/SiVa/siva3/deployment_guide/
  - https://open-eid.github.io/SiVa/siva/overview/
tags: [siva, architecture, hexagonal, ports-adapters, proxy, decorator, code-structure, patterns, comparison]
confidence: high
volatility: warm
summary: "Architetturalmente i due divergono per una causa a monte: il vincolo multi-libreria. SiVa deve supportare formati legacy estoni (DDOC via JDigiDoc, BDOC via DigiDoc4J) oltre a DSS-fork per PAdES/ASiC, quindi decompone per FORMATO+LIBRERIA con un validation-proxy che smista per documentType, e isola i conflitti di JAR a livello di DEPLOYMENT (un Tomcat per servizio). sign-verify-2 ha un solo motore (DSS 6.4 upstream), quindi decompone per RESPONSABILITA (esagonale, porte/adapter, ArchUnit), delega lo smistamento di formato alla factory di DSS, e usa il decorator solo per l'unica cosa che DSS non fa (TSD RFC 5544)."
---

# Architettura & pattern — SiVa vs sign-verify-2 (verifica firma)

Confronto focalizzato su **strutturazione del codice, architettura e pattern** del percorso di
verifica, tra [[entities/siva|open-eid/SiVa]] e [[entities/sign-verify-2]]. Compagno di
[[analyses/verification-siva-vs-sign-verify-2]] (comportamento/feature) e
[[analyses/extraction-siva-vs-sign-verify-2]]. Fatti SiVa ancorati a [[sources/SRC-2026-06-28-001]]
e riconfermati 2026-07-01 su
<https://open-eid.github.io/SiVa/siva3/structure_and_activities/> (component model),
<https://open-eid.github.io/SiVa/siva3/deployment_guide/>,
<https://open-eid.github.io/SiVa/siva/overview/>. Fatti sign-verify-2 da
[[concepts/hexagonal-architecture]], [[concepts/design-first-openapi]], [[entities/dssvalidatoradapter]].

## La causa a monte: vincolo mono- vs multi-libreria

La differenza architetturale **non è una scelta di stile**: discende da un requisito diverso.

- **SiVa** deve validare formati **legacy estoni** (DDOC, BDOC) che richiedono librerie **non-DSS**
  (JDigiDoc per DDOC; DigiDoc4J per BDOC), oltre al **fork** DigiDoc4J-DSS per PAdES/CAdES/XAdES/ASiC
  e `asicverifier` per l'ASiC-E X-Road. Più librerie con dipendenze in conflitto → l'architettura è
  organizzata per **contenere quel conflitto**.
- **sign-verify-2** ha un **unico motore**, DSS 6.4 upstream, per tutti i formati eIDAS (nessun
  obbligo di formato legacy) → nessun conflitto di librerie da contenere.

Tutto il resto discende da qui.

## Confronto strutturale

| Aspetto | SiVa | sign-verify-2 |
|---|---|---|
| Stile architetturale | Monolite **modulare multi-webapp** | **Esagonale** (ports & adapters), singolo servizio |
| Criterio di decomposizione | Per **formato + libreria** (Generic / DDOC / BDOC / X-Road) | Per **responsabilità** (dominio ↔ porte ↔ adapter) |
| Dispatch di formato | **Validation-proxy / selector** esplicito: instrada per `documentType`; nessun match → errore | Delegato alla **factory DSS** `SignedDocumentValidator.fromDocument()` (auto-detect); nessun selettore custom |
| Isolamento | A livello di **deployment**: *un Tomcat per servizio* per evitare conflitti di JAR | A livello di **codice**: confini esagonali imposti da **ArchUnit** ([[concepts/hexagonal-architecture]]) |
| Motore/i di validazione | JDigiDoc (DDOC) · DigiDoc4J (BDOC) · **fork** DigiDoc4J-DSS (PAdES/…) · asicverifier (X-Road) | **DSS 6.4 upstream**, singolo |
| Pattern chiave | **Proxy/Selector** (routing per tipo) · Adapter per-libreria | **Ports & Adapters** · **Decorator** (`RecursiveExtractionAdapter` su `DssExtractionAdapter`) · `@Primary` · circuit-breaker come decoratore di resilienza |
| Contratto API | Definito nel codice (Web API module) | **Design-first OpenAPI** → generazione `api.spi`/`api.dto`, protetto da `OpenApiContractIT` ([[concepts/design-first-openapi]]) |
| Mapping report | Report Java per-servizio → Web API | **DTO via mapper** (`SimpleReportMapper`), i controller non espongono entità |
| Topologia di rete | Più webapp/porte (8080 webapp, 8081 X-Road, servizi separati) | **Un** servizio (adapter async interni: job queue + worker + webhook) |
| Estensione a nuovo formato | Nuovo validation-service + registrazione nel proxy (+ possibile nuovo Tomcat) | Nessuna, se DSS lo supporta; altrimenti **nuovo adapter/decorator** dietro la porta |

## Pattern a confronto

### SiVa — Proxy/Selector + isolamento per deployment
Il **validation-proxy** è il cuore strutturale: riceve la richiesta, legge `documentType`, sceglie il
validation-service corrispondente, converte e inoltra; nessun match → eccezione/errore. È un
**Strategy/Registry** keyed sul tipo di documento, ma reso concreto come **moduli separati** perché
le librerie sottostanti non possono coabitare nella stessa JVM (JAR-hell). Conseguenza: il pattern
"un Tomcat per servizio" — isolamento forte ma **peso operativo alto** e coupling alla topologia.

### sign-verify-2 — Ports & Adapters + Decorator, **due motori in-process**
Il dominio non conosce DSS: parla con **porte** (`ValidatorPort`, `ExtractionPort`,
`DocumentStoragePort`, callback…). Gli **adapter** (`DssValidatorAdapter`, `DssExtractionAdapter`)
traducono verso DSS. Per i formati coperti da DSS lo smistamento **non esiste come codice
applicativo**: lo fa la factory `SignedDocumentValidator.fromDocument()`.

**sign-verify-2 ha però GIÀ un formato fuori DSS: l'RFC 5544 TSD** (DSS 6.4 non ha factory per
`id-aa-timeStampedData`). È il caso che mostra il pattern reale con cui il progetto integra un
**secondo motore** (BouncyCastle) — e **non** è un proxy/selector keyed sul tipo dichiarato, ma un
**Decorator con try-fallback** (una forma di Chain of Responsibility):

- estrazione → `RecursiveExtractionAdapter` avvolge `DssExtractionAdapter`: `tryUnwrapTsd(bytes)` con
  BouncyCastle; se sono TSD li sbuccia (ricorsivamente), altrimenti **delega** a DSS;
- verifica → `TsdAwareValidatorAdapter` fa lo stesso davanti a `DssValidatorAdapter`.

Il routing è quindi **content-attempt** (prova a parsare, poi ripiega), non basato sul `documentType`
dichiarato dal client come in SiVa. Il secondo motore (BouncyCastle) vive **nella stessa JVM** del
motore DSS: nessun Tomcat separato, nessun JAR-hell — perché DSS e BouncyCastle coesistono (DSS già
dipende da BC). Il circuit-breaker resta sulla sola entry pubblica (vedi
[[2026-07-01-ll-extraction-recursive-unwrap]]). ArchUnit impedisce al dominio di dipendere dagli
adapter: l'isolamento è **statico/di compilazione**, non di deployment.

## Perché la differenza conta
- **Peso operativo:** SiVa paga multi-webapp/multi-Tomcat come prezzo del multi-libreria;
  sign-verify-2 resta un singolo deployable. Per una PA che non ha bisogno di DDOC/BDOC, la topologia
  SiVa è complessità non necessaria.
- **Estendibilità:** aggiungere un formato in SiVa può significare un nuovo servizio + nodo; in
  sign-verify-2 è gratis se DSS lo copre, altrimenti un adapter dietro una porta.
- **Testabilità:** i confini esagonali + ArchUnit + design-first OpenAPI danno a sign-verify-2
  gate automatici (architettura e contratto) che il monolite modulare di SiVa non impone.
- **Fork-lag:** la scelta SiVa di **forkare DSS** (per allinearlo ai formati EE) è insieme causa e
  sintomo del multi-libreria; sign-verify-2 evitando i formati legacy resta su upstream.

## Il caso già risolto: TSD è un formato fuori DSS, senza proxy né multi-webapp
Il confronto con SiVa avrebbe suggerito un proxy/selector per un motore non-DSS. sign-verify-2 ha
affrontato **esattamente** questo scenario con l'RFC 5544 TSD e ha scelto una via diversa e più
leggera: **secondo motore (BouncyCastle) nella stessa JVM, integrato via Decorator + try-fallback**,
non via selector keyed sul tipo dichiarato né via servizio/Tomcat separato. Questo è il **precedente
architetturale** del progetto per "formato fuori dal motore principale":

- **Vantaggio vs proxy SiVa:** nessuna dipendenza dal `documentType` del client (il TSD viene
  riconosciuto *provando* a parsarlo, non fidandosi di un'etichetta); nessun nodo aggiuntivo.
- **Quando invece servirebbe un selector:** solo se i motori multipli avessero dipendenze **in
  conflitto** e non potessero coabitare nella JVM — il vero motivo per cui SiVa è multi-webapp. Finché
  i motori coesistono (DSS + BC), il decorator in-process è preferibile.

Quindi da SiVa si conferma un **anti-requisito**: la sua topologia è la risposta a un problema
(JAR-hell da formati legacy) che sign-verify-2 non ha — e il TSD dimostra che un formato extra si
aggiunge senza importarne la complessità.

## Anti-pattern SiVa da NON copiare
- **Un Tomcat per servizio** — è una *conseguenza* del JAR-hell multi-libreria, non un obiettivo;
  l'esagonale mono-motore lo evita.
- **Fork della libreria core (DSS)** — coupling e ritardo upstream.

## Related
- [[analyses/verification-siva-vs-sign-verify-2]] · [[analyses/extraction-siva-vs-sign-verify-2]] · [[analyses/siva-vs-sign-verify-2]]
- [[concepts/hexagonal-architecture]] · [[concepts/design-first-openapi]]
- [[concepts/siva-deployment-ops]] · [[concepts/siva-rest-interface]]
- [[entities/siva]] · [[entities/sign-verify-2]] · [[entities/dss]] · [[entities/dssvalidatoradapter]]
- [[2026-07-01-ll-extraction-recursive-unwrap]]
