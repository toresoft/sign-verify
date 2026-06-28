---
title: "Plan: verifica di file TSD (RFC 5544)"
type: plan
format: roadmap
sources:
  - analyses/verifica-file-tsd
  - analyses/tsd-dto-mapping
  - concepts/rfc5544-tsd
  - concepts/dss-format-detection
  - concepts/dss-timestamp-api
  - concepts/etsi-en-319-102-1-validation
  - concepts/reports
  - sources/tsd-is-cades-walk-not-detached-timestamp
  - sources/rfc5544-tsd-standard
  - sources/dss-format-detection-research
  - sources/dss-timestamp-api-research
generated: 2026-06-28
---

# Plan: verifica di file TSD (RFC 5544)

> Generato dal wiki [Digital signature verification](../meta/index.md) (11 articoli consultati)

## Executive Summary

I file `.tsd` prodotti da ArubaSign/GoSign/Namirial sono **RFC 5544 TimeStampedData**
(`ContentInfo.contentType = id-aa-timeStampedData`, OID `1.2.840.113549.1.9.16.1.31`),
**non** CAdES `id-signedData`. Test empirico (`Rfc5544TsdRoutingTest`, 2026-06-27) ha confermato
che **DSS 6.4 non ha factory per TSD**: `SignedDocumentValidator.fromDocument()` lancia
`IllegalInputException("A CMS file is expected : Not a valid CAdES file")` → l'endpoint
`/verifications` attuale **rifiuta** questi file con `signatureParseError`.

Soluzione: un nuovo **`TsdAwareValidatorAdapter`** (decorator davanti a `DssValidatorAdapter`) che,
sul fallimento, usa **Bouncy Castle** (`CMSTimeStampedData`, bcpkix 1.84 già nel classpath) per
sbustare l'inner `.p7m` e per validare anche i **token RFC 3161 del wrapper** via
`DetachedTimestampValidator`. L'esito segue una **semantica di aggregazione strict** e il
`VerificationResponse` viene **arricchito** con array `signatures[]`/`timestamps[]`.

## Decisioni acquisite (interview 2026-06-28)

| # | Decisione | Scelta |
|---|---|---|
| 1 | Dove implementare | **Nuovo `TsdAwareValidatorAdapter`** (decorator/pre-filter) |
| 2 | Marche RFC 3161 del wrapper | **Validare** anche i token wrapper (non solo inner `.p7m`) |
| 3 | Aggregazione esito | **Strict** — qualsiasi `INDETERMINATE` su marca → overall `INDETERMINATE` |
| 4 | Contratto API | **Arricchire** con `signatures[]`/`timestamps[]` |

## Architecture Decisions

### Decision 1: routing TSD via Bouncy Castle, non via DSS

**Context**: [[concepts/rfc5544-tsd]] e [[analyses/verifica-file-tsd]] documentano, con test empirico,
che `CMSDocumentAnalyzer.isSupported()` restituisce `true` per un TSD (byte `0x30`, `isTimestampToken()`
swallow→false) ma il successivo build delle firme lancia `IllegalInputException`. DSS 6.4 non ha
validator per `id-aa-timeStampedData`.

**Options considered**:
- A: attendere/forzare supporto DSS — non esiste in 6.4 (per [[concepts/rfc5544-tsd]]).
- B: parsing custom Bouncy Castle `CMSTimeStampedData` → estrarre `getContent()` (inner `.p7m`) e
  `getTimeStampTokenEvidence()` (token RFC 3161) (per [[analyses/verifica-file-tsd]] §"Approccio implementativo").

**Decision**: Opzione B. È l'unico path funzionante; BC è già nel classpath.

**Consequences**: dipendenza esplicita da `org.bouncycastle.tsp.cms.CMSTimeStampedData`; gestione
del caso `getContent()` vuoto (TSD che marca un riferimento `dataUri` senza contenuto embedded).

### Decision 2: adapter decorator dedicato

**Context**: il `DssValidatorAdapter` attuale (riga 40-42) cattura ogni eccezione di `fromDocument()`
e la trasforma in `signatureParseError`. [[sources/tsd-is-cades-walk-not-detached-timestamp]] avverte
di non basarsi sull'estensione e di non sporcare il path CAdES.

**Options considered**:
- A: estendere `DssValidatorAdapter` nel catch esistente — meno classi, ma mescola responsabilità.
- B: nuovo `TsdAwareValidatorAdapter` che implementa `SignatureValidatorPort` e delega al
  `DssValidatorAdapter`; intercetta solo il fallimento "non-CAdES" e tenta il path TSD.

**Decision**: Opzione B (scelta utente). Path CAdES intatto, logica RFC 5544 isolata e testabile.

**Consequences**: serve decidere il wiring (il decorator diventa il bean `SignatureValidatorPort`
primario; il `DssValidatorAdapter` resta delegate). Resilience4j/circuit-breaker va mantenuto sul
delegate ([[concepts/circuit-breaker]]).

### Decision 3: validare anche le marche del wrapper TSD

**Context**: [[concepts/rfc5544-tsd]] — il wrapper TSD porta `temporalEvidence` = SEQUENCE di
TimeStampToken RFC 3161 sul contenuto. [[concepts/dss-timestamp-api]] / [[analyses/tsd-dto-mapping]]
descrivono il mapping dei timestamp.

**Decision**: dopo lo sbustamento, validare ogni `TimeStampToken` del wrapper con
`DetachedTimestampValidator` (detached, su `getContent()` come imprint), aggregandolo nel report
come marca di tipo wrapper, distinta dalle marche embedded del CAdES interno.

**Consequences**: doppia validazione (inner CAdES + wrapper TS); il DTO deve distinguere la marca
"esterna" del TSD dalle marche embedded.

### Decision 4: aggregazione strict + DTO arricchito

**Context**: [[analyses/tsd-dto-mapping]] §"Semantica aggregazione" elenca strict vs lenient;
`INDETERMINATE` ≠ `FAILED` ([[concepts/etsi-en-319-102-1-validation]]). Il `VerificationResponse`
attuale (openapi.yaml ~riga 615) è piatto: `signatureFormat/indication/subIndication/signatureCount`
+ mappa `reports` opaca.

**Decision**: **strict** — qualunque `INDETERMINATE` su una marca (wrapper o embedded) abbassa
l'overall ad `INDETERMINATE`. DTO esteso con `signatures[]` (id, indication, subIndication,
signatureFormat, signatureLevel, signedBy, claimedSigningTime, bestSignatureTime, timestamps[],
counterSignatures[]) e `timestamps[]` (id, type, productionTime, producedBy, indication,
qualification) secondo [[analyses/tsd-dto-mapping]].

**Consequences**: cambio di contratto OpenAPI (design-first) → guardato da `OpenApiContractIT`;
aggiornare assembler/DTO; i campi piatti restano per retro-compatibilità.

## Implementation Phases

### Phase 1: Rilevamento + sbustamento RFC 5544 (effort: M)

**Goal**: accettare e instradare un `.tsd` invece di rifiutarlo.

**Tasks**:
- [ ] Promuovere `Rfc5544TsdRoutingTest` (untracked) a test di regressione del comportamento DSS.
- [ ] Creare `TsdAwareValidatorAdapter implements SignatureValidatorPort`, delegate = `DssValidatorAdapter`.
- [ ] Nel catch del delegate (solo `IllegalInputException`/parse non-CAdES), tentare
      `new CMSTimeStampedData(documentBytes)`; se OK estrarre `getContent()` → inner `.p7m`.
- [ ] Re-instradare l'inner doc (`InMemoryDocument`, nome derivato) a `SignedDocumentValidator.fromDocument()`.
- [ ] Gestire `getContent()` null/vuoto → `AppException.signatureParseError("TSD wrapper without inner document")`.
- [ ] Wiring Spring: decorator come bean `SignatureValidatorPort` primario; preservare circuit-breaker sul delegate.

**Dependencies**: nessuna.
**Validation**: unit test: un `.tsd` sintetico (inner = `.p7m` di test valido) viene validato
invece di lanciare `signatureParseError`; un `.p7m` puro continua a passare dal path CAdES invariato.
**Wiki grounding**: [[analyses/verifica-file-tsd]] §"Approccio implementativo", [[concepts/rfc5544-tsd]].

### Phase 2: Validazione marche RFC 3161 del wrapper (effort: M)

**Goal**: validare le marche esterne del TSD, non solo l'inner CAdES.

**Tasks**:
- [ ] Estrarre `tsd.getTimeStampTokenEvidence()` → `TimeStampToken[]`.
- [ ] Per ogni token: `DetachedTimestampValidator` con imprint = `getContent()` + `CertificateVerifier`.
- [ ] Aggregare i `Reports` del wrapper con quelli dell'inner CAdES in un risultato unificato.
- [ ] Marcare la provenienza della marca (wrapper TSD vs embedded CAdES) per il DTO.

**Dependencies**: Phase 1.
**Validation**: test con TSD la cui TSA è in Trusted List → marca `PASSED`; TSA fuori Trusted List →
`INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` sulla marca.
**Wiki grounding**: [[concepts/rfc5544-tsd]], [[entities/detachedtimestampvalidator]], [[concepts/dss-timestamp-api]].

### Phase 3: Aggregazione strict dell'esito (effort: S)

**Goal**: esito overall coerente tra firma B-level e marche.

**Tasks**:
- [ ] Implementare regola strict: `overall = worst-of(firme master, marche wrapper, marche embedded)`
      dove qualsiasi `INDETERMINATE` → overall `INDETERMINATE`, qualsiasi `FAILED` → `FAILED`.
- [ ] Centralizzare in un componente di aggregazione riusabile (anche per CAdES/PAdES standard).
- [ ] Documentare la semantica nella descrizione OpenAPI del campo `indication`.

**Dependencies**: Phase 2.
**Validation**: test matrice: firma PASSED + marca INDETERMINATE → overall INDETERMINATE (strict).
**Wiki grounding**: [[analyses/tsd-dto-mapping]] §"Semantica aggregazione", [[concepts/etsi-en-319-102-1-validation]].

### Phase 4: Arricchimento DTO `signatures[]`/`timestamps[]` (effort: M, design-first)

**Goal**: esporre tutte le firme + marche nel response.

**Tasks**:
- [ ] Editare `src/main/resources/openapi/openapi.yaml`: aggiungere `signatures[]` e (a livello firma)
      `timestamps[]`, `counterSignatures[]`, `bestSignatureTime`, `signatureLevel`, `signedBy`;
      mantenere i campi piatti esistenti per retro-compatibilità.
- [ ] Rigenerare interfacce/DTO (OpenAPI Generator) e aggiornare l'assembler con il mapping di
      [[analyses/tsd-dto-mapping]] (`SimpleReport`/`DiagnosticData` → DTO, ricorsione counter-sig).
- [ ] `mvn spotless:apply`; verificare `OpenApiContractIT`.

**Dependencies**: Phase 3.
**Validation**: `OpenApiContractIT` verde; response di un `.tsd` multi-firma elenca ogni firma e
ogni marca con `bestSignatureTime` popolato.
**Wiki grounding**: [[analyses/tsd-dto-mapping]] (struttura DTO + tabella mapping + codice iterazione),
[[concepts/design-first-openapi]], [[concepts/reports]].

### Phase 5: Corpus di test e e2e (effort: M)

**Goal**: copertura realistica del caso PA.

**Tasks**:
- [ ] **Generare** corpus sintetico `.tsd` (nessun campione pubblico esiste — [[analyses/verifica-file-tsd]]
      §"Test vectors"): wrappare `.p7m` CAdES-T di test in `CMSTimeStampedData` via BC.
- [ ] Casi: TSD su `.p7m` CAdES-T; multi-firma; counter-firma; TSA non in Trusted List (negativo).
- [ ] (Best effort) richiedere campioni reali ArubaSign/GoSign per validazione finale.
- [ ] Failsafe IT che esercita `/verifications` end-to-end con `.tsd`.

**Dependencies**: Phase 1-4.
**Validation**: `mvn verify` verde con i nuovi IT; asserzioni su counts, indication, timestamp list.
**Wiki grounding**: [[analyses/verifica-file-tsd]] §"Piano di test" e §"Test vectors".

## Risks & Mitigations

| Rischio | Fonte | Mitigazione |
|---|---|---|
| Nessun corpus `.tsd` pubblico | [[analyses/verifica-file-tsd]] | Generare TSD sintetici con BC; richiedere campioni Aruba/GoSign |
| `getContent()` vuoto (TSD con `dataUri`, no contenuto embedded) | [[concepts/rfc5544-tsd]] (struttura ASN.1) | Errore esplicito `signatureParseError`; fuori scope la risoluzione `dataUri` |
| Decorator rompe circuit-breaker/fallback del delegate | [[concepts/circuit-breaker]] | Mantenere Resilience4j sul `DssValidatorAdapter`; decorator sottile |
| Cambio contratto OpenAPI rompe client | [[concepts/design-first-openapi]] | Campi additivi; conservare i campi piatti; `OpenApiContractIT` |
| Doppia interpretazione `.tsd` come `.tsr` (timestamp nudo) | [[sources/tsd-is-cades-walk-not-detached-timestamp]] | Rilevare per contenuto (OID), mai per estensione |

## Open Questions

- Risoluzione del `dataUri` (TSD che marca un riferimento esterno) — fuori scope ora?
- TSD annidati / catena di più token wrapper per long-term preservation: gestire tutta la catena o solo il primo?
- Il decorator deve gestire anche il vero `.tsr` (timestamp detached nudo) o resta endpoint separato? (wiki suggerisce endpoint distinto)

## Sources Consulted

- [[analyses/verifica-file-tsd]] — routing RFC 5544, conferma empirica DSS, codice BC, piano test
- [[analyses/tsd-dto-mapping]] — struttura DTO, tabella mapping DSS→JSON, codice iterazione, aggregazione
- [[concepts/rfc5544-tsd]] — definizione ASN.1, distinguo TSD vs CAdES-T, normativa IT, comportamento DSS
- [[concepts/dss-format-detection]] — meccanismo autodetect per contenuto
- [[concepts/dss-timestamp-api]] — TimestampWrapper/SignatureWrapper/SimpleReport
- [[concepts/etsi-en-319-102-1-validation]] — semantica indication/subIndication
- [[concepts/reports]] — forma SimpleReport/DiagnosticData
- [[sources/tsd-is-cades-walk-not-detached-timestamp]] — correzione: detection per contenuto, no ramo per estensione
- [[sources/rfc5544-tsd-standard]] — RFC 5544/5955, adozione PA italiana
