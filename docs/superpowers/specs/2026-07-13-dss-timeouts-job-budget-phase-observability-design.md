# Design — Timeout DSS espliciti, budget di tempo per job, osservabilità di fase

- **Data:** 2026-07-13
- **Stato:** approvato, pronto per il piano di implementazione
- **Contesto:** hardening emerso dall'analisi di un job async con 5 firme rimasto bloccato
  a lungo su OCSP/CRL irraggiungibili, senza modo di capire dove fosse fermo. Il bug di
  race optimistic-lock che *perdeva* il risultato è già stato risolto separatamente
  (commit `c71ba5d`, `ValidationJobConcurrencyTest`); questo design costruisce sopra la
  macchina di update condizionali introdotta lì.

## Obiettivo

Tre feature di hardening ortogonali sul percorso di validazione async:

1. **Timeout DSS espliciti** — boundare e rendere configurabili le chiamate di rete
   (OCSP/CRL/AIA e TSL), oggi affidate ai default di `CommonsDataLoader`.
2. **Budget di tempo per job** — un tetto wall-clock sulla singola validazione, con esito
   deterministico `job.timeout`, senza interruzione forzata del thread DSS.
3. **Osservabilità di fase** — sapere *dove* un job in corso è fermo, sia sul singolo job
   in volo (fase persistita, interrogabile via API/DB) sia in aggregato (log + metriche).

Non-goal: cambiare la semantica di verifica eIDAS (revoca irraggiungibile continua a
degradare a INDETERMINATE con job COMPLETED); reclaim immediato dello slot worker su
timeout (richiederebbe cancellazione forzata, esplicitamente scartata).

## Decisioni di design (fissate col committente)

- **Budget:** watchdog + esito deterministico. Niente `Future.cancel`/interruzione.
- **Osservabilità:** fase **persistita** sul job + log strutturati + Timer Micrometer.
- **Over-budget:** `FAILED` secco con `job.timeout`, **nessun** retry.

---

## Sezione 1 — Timeout DSS espliciti

### Problema

In `DssConfiguration.certificateVerifier` le tre fonti online vengono create senza
`DataLoader`, quindi ciascuna istanzia il proprio `CommonsDataLoader` di default:

```java
cv.setAIASource(new DefaultAIASource());
cv.setOcspSource(new OnlineOCSPSource());
cv.setCrlSource(new OnlineCRLSource());
```

Le fetch di revoca/AIA durante la validazione non sono né bounded da noi né configurabili.
Anche il loader interno a `fileCacheDataLoader` (usato per la TSL) è `new CommonsDataLoader()`
con i default.

### Design

- Nuova classe `DssHttpProperties` (`@ConfigurationProperties("app.dss.http")`) con quattro
  `Duration`: `connectTimeout`, `connectionRequestTimeout`, `responseTimeout`,
  `socketTimeout`. Registrata via `@EnableConfigurationProperties` in `DssConfiguration`.
- Nuovo bean `CommonsDataLoader revocationDataLoader(DssHttpProperties props)` che applica i
  quattro setter disponibili in DSS 6.4:
  - `setTimeoutConnection(int ms)` — connessione TCP
  - `setTimeoutConnectionRequest(int ms)` — lease dal pool di connessioni
  - `setTimeoutResponse(int ms)` — attesa risposta
  - `setTimeoutSocket(int ms)` — inattività tra pacchetti
  - Conversione `Duration.toMillis()` → `int` con guardia overflow (cap a `Integer.MAX_VALUE`),
    valori validati al boundary (positivi).
- Iniettare `revocationDataLoader` nelle tre fonti:
  `new OnlineOCSPSource(revocationDataLoader)`, `new OnlineCRLSource(revocationDataLoader)`,
  `new DefaultAIASource(revocationDataLoader)` (costruttori/`setDataLoader` verificati sul jar 6.4).
- Applicare gli stessi timeout al `CommonsDataLoader` dentro `fileCacheDataLoader` (una TSL host
  lenta non deve bloccare il refresh).

### Default proposti

```yaml
app.dss.http:
  connect-timeout: 3s
  connection-request-timeout: 3s
  response-timeout: 10s
  socket-timeout: 10s
```

### Ruolo nel budget

Questi timeout sono la base "budget-by-construction": boundano ogni chiamata di rete e quindi
limitano quanto a lungo può drenare un thread orfano andato oltre budget (Sezione 2).

### Semantica di verifica

**Invariata.** Revoca irraggiungibile → DSS degrada a INDETERMINATE (comportamento eIDAS
corretto), job COMPLETED. Cambiamo solo la latenza per-chiamata, non l'esito.

---

## Sezione 2 — Budget di tempo per job (watchdog per-istanza)

### Perché NON un bulk sweep in DB

Un watchdog che facesse `UPDATE ... WHERE status=RUNNING AND startedAt < cutoff` **non è
sicuro in multi-istanza**: non distingue "job vivo ma lento sull'istanza B" da "job crashato
sull'istanza B" (entrambi hanno `startedAt` vecchio) e ucciderebbe job legittimamente in corso
altrove. Il deployment è esplicitamente multi-istanza (cfr. commento di `resetStaleRunning`).

### Design — deadline in memoria, per-istanza

Il budget lo fa rispettare **solo l'istanza che possiede il job**:

- In `ValidationWorker`: `ConcurrentHashMap<UUID, Instant> deadlines`.
  - Alla submit su `validationExecutor`: `deadlines.put(id, now + maxValidationTime)`.
  - Nel `finally` di `process(id)`: `deadlines.remove(id)`.
- Nuovo metodo `@Scheduled enforceBudgets()` (tick `budget-check-interval`, default 15s):
  per ogni entry oltre deadline → `repo.markTimedOut(id, now)` + rimozione dell'entry.
  Tocca **solo** i job registrati da questa istanza.
- `markTimedOut(id, now)` — bulk update condizionale (idioma di `expireOverdue`):
  ```sql
  UPDATE ValidationJob
     SET status = FAILED,
         errorCode = 'job.timeout',
         errorMessage = 'validation exceeded time budget',
         completedAt = :now,
         nextCallbackAt = CASE WHEN callbackUrl IS NOT NULL THEN :now ELSE nextCallbackAt END,
         version = version + 1
   WHERE id = :id AND status = RUNNING
  ```
  Se il job non è più RUNNING (già completato, requeued o scaduto) → 0 righe, no-op.
- Il thread orfano continua a drenare (bounded dai timeout della Sezione 1). Quando `validate()`
  finalmente ritorna, `markCompleted`/`markFailed` trovano `status != RUNNING` → 0 righe →
  no-op loggato (macchina già costruita nel fix `c71ba5d`).

### Vincoli e interazione con lo sweep stale-running

- `max-validation-time` (default 5m) **deve** essere `< stale-running-timeout` (30m). Assertion
  allo startup (fail-fast se configurato male).
- Con budget << stale, il watchdog scatta sempre prima per i job vivi-ma-lenti. Lo sweep
  stale-running a 30m resta **solo** per il crash recovery: istanza morta → deadline in memoria
  persa → il job viene requeued dopo 30m (non "timed out": un crash non è colpa dell'input).
- Nuovo codice errore `Errors.JOB_TIMEOUT = "job.timeout"`, mappato a `application/problem+json`
  come già fa `job.expired`.

### Trade-off noto

Un orfano tiene occupato il suo slot worker + permesso semaphore finché `validate()` non ritorna,
riducendo la concorrenza effettiva fino al drain. È il prezzo di non interrompere forzatamente
Apache HttpClient (non affidabilmente interrompibile via `Thread.interrupt`); i timeout DSS
tengono il drain bounded. Il reclaim immediato richiederebbe `Future.cancel(true)`, scartato.

### Config

```yaml
app.async.worker:
  max-validation-time: 5m       # DEVE essere < stale-running-timeout
  budget-check-interval: 15s
```

---

## Sezione 3 — Osservabilità di fase

### Design

- Nuovo enum `ValidationPhase { STORAGE_READ, POLICY_RESOLVE, DSS_VALIDATE, RESULT_STORE }`.
- Flyway **`V7__validation_job_phase.sql`**: colonne `phase VARCHAR` + `phase_updated_at
  TIMESTAMPTZ` (nullable). Campi corrispondenti su `ValidationJob`; Hibernate resta
  `ddl-auto: validate`.
- `repo.setPhase(id, phase, now)` — bulk update **version-neutral** condizionale
  (idioma di `touchLastAccessed`):
  ```sql
  UPDATE ValidationJob SET phase = :phase, phaseUpdatedAt = :now
   WHERE id = :id AND status = RUNNING
  ```
  Non bumpa `@Version` (non racea mai la finalize del worker) e la condizione `status=RUNNING`
  evita di scrivere fase su un job già chiuso dal watchdog mentre l'orfano drena. Chiamata a
  inizio di ogni fase in `process()`.
- GET `/jobs/{id}` espone `phase` + `phaseUpdatedAt` nel body: un job RUNNING bloccato mostra
  **esattamente** dove sta (es. `phase=DSS_VALIDATE`, `phaseUpdatedAt` vecchio). Risolve la
  difficoltà diagnostica originale senza dover grep-are i log.
- Log strutturati: INFO all'ingresso di `DSS_VALIDATE` e un INFO di completamento con durata
  totale + breakdown per fase. `jobId` passato come argomento esplicito (l'executor async non
  propaga l'MDC `requestId`/`principalId`).
- Micrometer: `Timer signverify.validation.phase{phase=…}` per percentili su Prometheus;
  `Counter signverify.validation.timeout` incrementato quando il watchdog scatta. Registrati via
  `MetricsConfiguration` / `MeterRegistry` iniettato nel worker.
- Bonus: il messaggio di `markTimedOut` può citare la fase corrente ("timed out during
  DSS_VALIDATE") leggendola dal job prima dell'update.

---

## Impatto su file esistenti

- `config/DssConfiguration.java` — nuovo `DssHttpProperties`, bean `revocationDataLoader`,
  iniezione nelle tre fonti + loader TSL.
- `application.yaml` — sezioni `app.dss.http` e `app.async.worker.{max-validation-time,
  budget-check-interval}`.
- `application/ValidationWorker.java` — registry `deadlines`, `@Scheduled enforceBudgets`,
  chiamate `setPhase` per fase, log + Timer per fase, messaggio timeout con fase.
- `persistence/ValidationJobRepository.java` — `markTimedOut`, `setPhase`.
- `domain/exception/Errors.java` — `JOB_TIMEOUT`.
- `domain/model/ValidationJob.java` + `ValidationPhase.java` (nuovo) — campi `phase`,
  `phaseUpdatedAt`.
- `api/AsyncVerificationController.java` (+ DTO/OpenAPI) — `phase`/`phaseUpdatedAt` nella
  response del job.
- `db/migration/V7__validation_job_phase.sql` (nuovo).

## Testing

- `DssHttpProperties` → loader: i quattro setter ricevono i millis configurati (build del bean,
  lettura dei getter).
- `markTimedOut` / `setPhase`: `@DataJpaTest` sullo stile di `expireOverdue` /
  `ValidationJobConcurrencyTest` — condizionali su `status=RUNNING`; `setPhase` version-neutral
  (una copia caricata prima resta valida per una finalize successiva).
- Watchdog: unit Mockito — `enforceBudgets` marca solo le entry oltre deadline e le rimuove;
  `process` registra e poi rimuove la deadline; nessun job non registrato viene toccato
  (sicurezza multi-istanza by construction).
- GET espone `phase`/`phaseUpdatedAt` (controller test); `OpenApiContractIT` aggiornato se lo
  schema del job cambia.
- Micrometer: Timer/Counter presenti e registrati.
- Startup assertion: contesto non parte se `max-validation-time >= stale-running-timeout`.
- Regressione: `DssRevocationNetworkIT` deve reggere coi nuovi default di timeout.

## Rischi

- Timeout troppo aggressivi → più risultati INDETERMINATE (revoca non recuperata in tempo).
  Mitigato da default prudenti e piena configurabilità.
- Orfani che occupano slot worker fino al drain → concorrenza ridotta sotto molti job lenti.
  Bounded dai timeout DSS; monitorabile via `signverify.async.jobs.running` +
  `signverify.validation.timeout`.
