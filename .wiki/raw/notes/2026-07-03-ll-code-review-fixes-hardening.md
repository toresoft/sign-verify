---
title: "Lessons Learned: fix da tre round di code review (async pipeline, TSL sync, security)"
type: lessons-learned
source: session
date: 2026-07-03
tags: [lessons-learned, code-review, spring-transactions, archunit, jpa, mockito, tdd]
lesson_count: 7
category: notes
confidence: high
summary: "Triage di review esterne contro il call graph reale; pattern transazionali con self-invocation; @Version e bulk JPQL; port senza tipi DSS; smoke test DB-backed come rete per refactor"
---

# Lessons Learned: fix da tre round di code review (2026-07-02/03)

> Estratte dalla sessione che ha valutato 16 finding di review esterna su sign-verify-2,
> ne ha implementati ~12 in TDD (commit `80e30ff`, tag `0.12.3`) e respinti/ridimensionati 4.

## Lesson 1: verificare ogni claim di review contro il call graph reale, non contro la plausibilità

**Category**: pattern
**Context**: tre round di review esterna con 16 finding totali.
**Symptom**: finding plausibili ma con conseguenze sbagliate: "setCacheExpirationTime(0) re-downloads the EU LOTL every validation" (falso: il loader è cablato solo in TLValidationJob, il refresh è schedulato, mai per-validazione); "a JVM crash leaves a job stranded in RUNNING forever" (falso: la fase EXPIRED del cleanup lo raccoglie al TTL); "gate the overrides" (la risposta sync espone già `overridesApplied`).
**Root cause**: il reviewer ragiona sul codice locale, non su chi lo chiama né sulle mitigazioni altrove nel codebase.
**Fix**: per ogni finding: chi chiama? con che frequenza? esiste già una mitigazione (commento, sweep, campo esposto)? Poi classificare: valido / valido-ma-ridimensionato / contestato.
**Rule**: un finding di review si accetta solo dopo aver tracciato il call graph e cercato mitigazioni esistenti; la metà del valore del triage sta nei finding respinti con motivazione tecnica.

## Lesson 2: self-invocation Spring — l'annotazione morta si rimuove, la transazione si sposta dove serve

**Category**: pattern
**Context**: `ValidationWorker.poll()` chiama `this.process(id)` → proxy bypassato → `@Transactional` su `process()` mai attivo. Idem `CallbackWorker.dispatch()`.
**Root cause**: self-invocation bypassa il proxy AOP; il codebase lo sapeva già (commento esplicito su `claimForProcessing`) ma le annotazioni morte erano rimaste, fuorvianti.
**Fix**: rimosse le annotazioni con commento che documenta il design; la transazionalità vive sul metodo repository `@Modifying @Transactional` (claim atomico); crash recovery affidato a uno sweep `resetStaleRunning` in `poll()` invece che a una transazione lunga.
**Rule**: non "riparare" la self-invocation avvolgendo lavori lunghi (validazione DSS, HTTP) in una transazione DB — meglio commit puntuali + operazione di recovery idempotente; l'annotazione morta va comunque rimossa perché mente al lettore.

## Lesson 3: i bulk update JPQL bypassano @Version — bump manuale obbligatorio

**Category**: gotcha
**Context**: aggiunto `@Version` a `ValidationJob` (migration V4) per i lost update tra worker e cleanup; fase EXPIRED convertita a UPDATE condizionale bulk.
**Root cause**: Hibernate non incrementa la version nei bulk JPQL UPDATE/DELETE; senza bump il worker con copia stale non va in conflitto e il lock ottimistico non protegge nulla.
**Fix**: `SET ..., j.version = j.version + 1` esplicito in ogni bulk update che muta righe contese (`expireOverdue`, `resetStaleRunning`).
**Rule**: ogni bulk JPQL che tocca un'entità con `@Version` deve incrementare la version esplicitamente, o il lock ottimistico è solo decorativo.

## Lesson 4: un port non può avere tipi DSS in firma — si risolve togliendo l'argomento, e la regola ArchUnit mancava

**Category**: discovery
**Context**: fix della violazione esagonale `TslService` (application) → `adapter.dss.TrustedCertificateMirror`.
**Symptom**: il fix suggerito dal reviewer ("hide behind a port") era impossibile con la firma esistente: `sync(TrustedListsCertificateSource)` avrebbe portato un tipo DSS in `domain/port`, vietato da `domain_does_not_depend_on_dss`.
**Fix**: `TrustedListMirrorPort.sync()` **senza argomenti** — l'adapter si fa iniettare la `TrustedListsCertificateSource` (è già un bean); `Diff` spostato nel port. Aggiunta regola ArchUnit `application_does_not_depend_on_adapters`, vista fallire con 6 violazioni prima del refactor.
**Rule**: quando l'argomento di un metodo adapter è un tipo del vendor, il port si disegna spostando quell'argomento dentro l'adapter (constructor injection), non tipizzandolo nel domain; e ogni freccia architetturale che si vuole garantita deve avere la sua regola ArchUnit — quelle esistenti non coprivano application→adapter.

## Lesson 5: smoke test DB-backed prima del refactor — i mock su findAll() muoiono col refactor, H2+Flyway no

**Category**: pattern
**Context**: refactor del cleanup da 4×`findAll()` a query filtrate/bulk; i test esistenti stubavano `findAll()` e sarebbero diventati carta straccia.
**Fix**: prima dei fix, `JobCleanupSchedulerDbTest` — `@DataJpaTest` (H2 `MODE=PostgreSQL` + Flyway vera) con repository reale e solo storage/audit mockati; caratterizza il comportamento per fase. È rimasto verde attraverso tutto il refactor e ha validato gratis la migration V4.
**Symptom** (gotcha fixture): `NULL not allowed for column "REQUESTED_BY_PRINCIPAL_TYPE"` — le fixture di `validation_job` devono valorizzare `requestedByPrincipalType/Id`.
**Rule**: prima di rifattorizzare il *come* si interroga il DB, scrivere test di caratterizzazione contro il DB reale che fissano il *cosa*; i mock sulle query sono test dell'implementazione, non del comportamento.

## Lesson 6: config documentata ≠ config applicata — grep del binding, non del file yaml

**Category**: gotcha
**Context**: `app.callback.allowed-algorithms` e `hmac-default-algorithm` esistevano in `application.yaml` da sempre; il default era hardcodato in Java e l'allowlist non era referenziata da nessuna classe → qualunque `Mac` JCA accettato (HmacMD5 incluso), errore solo al dispatch.
**Fix**: bind via `@Value` + validazione al submit (400). Nota yaml: le liste per `@Value` devono essere stringhe comma-separated, non sequenze YAML (commento già presente nel file, ed è il motivo per cui la chiave era in quel formato).
**Symptom** (gotcha test): `AppException.badRequest(detail)` mette il testo in `getDetail()`, non nel message — asserire su `getDetail()`.
**Rule**: una chiave di configurazione "di sicurezza" esiste solo se un grep trova il suo binding in Java; la presenza nel yaml non prova nulla — cercare `@Value("${chiave}")`/`@ConfigurationProperties`, non la chiave nel file.

## Lesson 7: Mockito UnfinishedStubbingException se helper che creano mock sono argomenti di thenReturn

**Category**: gotcha
**Context**: `when(summary.getLOTLInfos()).thenReturn(List.of(lotlInfo(...)))` con `lotlInfo()` che internamente fa `mock()` + `when()`.
**Symptom**: `UnfinishedStubbingException: Unfinished stubbing detected... thenReturn() may be missing`.
**Root cause**: l'argomento di `thenReturn` viene valutato mentre lo stubbing esterno è ancora aperto; il `when()` annidato dentro l'helper corrompe lo stato dell'OngoingStubbing.
**Fix**: costruire i mock helper in variabili locali PRIMA della riga `when(...)`.
**Rule**: mai chiamare helper che fanno `mock()`/`when()` dentro gli argomenti di `thenReturn`; prima si costruiscono i collaboratori, poi si stubbano le risposte.
