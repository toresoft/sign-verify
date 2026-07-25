# Design: endpoint lista job filtrabile e paginata

**Data:** 2026-07-25
**Stato:** approvato (brainstorming)
**Ambito:** nuova API `GET /api/v1/verifications/jobs`

## Obiettivo

Esporre una vista **paginata e filtrabile** dei job di verifica asincroni. Oggi esiste
solo il recupero puntuale `GET /api/v1/verifications/jobs/{jobId}`; non c'è modo di
elencare i job né di cercarli per owner, stato o data di creazione.

La lista è una **vista sintetica** (soli metadati del job): non include mai il body del
risultato né i dettagli d'errore, che restano appannaggio del GET puntuale.

## Contratto API

`GET /api/v1/verifications/jobs`

### Query parameters

| Param | Tipo | Default | Note |
|---|---|---|---|
| `ownerId` | string | — | Filtra per id del principal richiedente. **Solo PRIVILEGED.** |
| `ownerType` | enum `API_KEY\|OAUTH_USER\|SYSTEM` | — | Opzionale, disambigua `ownerId`. **Solo PRIVILEGED.** |
| `status` | enum, **ripetibile** | — | `?status=COMPLETED&status=FAILED` → filtro `IN`. |
| `createdOn` | date `YYYY-MM-DD` | — | Espanso all'intera giornata **UTC** `[00:00, 24:00)`. **Mutuamente esclusivo** con `createdFrom`/`createdTo`. |
| `createdFrom` | date-time | — | Estremo inferiore **inclusivo** (`createdAt >= createdFrom`). |
| `createdTo` | date-time | — | Estremo superiore **esclusivo** (`createdAt < createdTo`). |
| `page` | integer | `0` | Indice pagina 0-based. |
| `size` | integer | `50` | **Clamp a max 200** (valori superiori ridotti a 200, non 400). |

Ordinamento: **fisso** `createdAt DESC` (più recenti prima).

Enum stati (allineato a `JobStatus`): `PENDING, RUNNING, COMPLETED, FAILED, DELIVERED,
DELIVERY_FAILED, DELETED`.

### Combinazioni dei filtri di data

I 4 casi richiesti si coprono così:

- **Giorno esatto** → `createdOn=2026-07-14`
- **Prima di una data** → solo `createdTo`
- **Dopo una data** → solo `createdFrom`
- **Tra due date** → `createdFrom` + `createdTo`

### Risposta `200`

Envelope identico a `/audit-log`:

```json
{
  "page": 0,
  "size": 50,
  "totalElements": 137,
  "totalPages": 3,
  "content": [
    {
      "jobId": "491ff5a4-5745-40bd-8bc3-119582f6e89e",
      "status": "COMPLETED",
      "requestedBy": { "type": "OAUTH_USER", "id": "user-123" },
      "createdAt": "2026-07-14T22:13:24.272673Z",
      "startedAt": "2026-07-14T22:14:10.351944Z",
      "completedAt": "2026-07-14T22:14:18.540216Z",
      "expiresAt": "2026-07-21T22:13:24.272673Z",
      "callbackAttempts": 0,
      "phase": "RESULT_STORE",
      "phaseUpdatedAt": "2026-07-14T22:14:18.53067Z"
    }
  ]
}
```

Campi di `JobSummary` (esattamente quelli richiesti): `jobId`, `status`, `requestedBy`,
`createdAt`, `startedAt`, `completedAt`, `expiresAt`, `callbackAttempts`, `phase`,
`phaseUpdatedAt`. `startedAt`/`completedAt`/`phase`/`phaseUpdatedAt` sono `nullable`.

Esclusioni deliberate:
- **`deliveredAt`** non è nell'elenco richiesto → omesso dalla lista (resta nel GET puntuale).
- Nessun `result`, `errorCode`, `error`, `lastCallbackError`.
- I job `DELETED` **compaiono** nella lista con `status: DELETED` (qui non c'è body di
  risultato, quindi non si applica la semantica `410` del GET puntuale).

## Autorizzazione e scoping

- **STANDARD** — vede **solo i propri** job. Lo scoping è imposto server-side aggiungendo
  al filtro `requestedByPrincipalType = principal.type()` **e**
  `requestedByPrincipalId = principal.id()`. Non è delegabile al client.
- **PRIVILEGED** — vede tutti i job; `ownerId`/`ownerType` filtrano su qualsiasi owner.
- Se uno **STANDARD** fornisce `ownerId` o `ownerType` → **`400`**
  (`owner filter requires PRIVILEGED`), così non interpreta erroneamente una lista vuota
  come "job di un altro owner". Non si fa fall-through silenzioso a "sé stesso".

## Validazione (tutte → `400` come `AppException.badRequest`, salvo diverso)

1. `createdOn` insieme a `createdFrom` e/o `createdTo` → `400`.
2. `createdFrom > createdTo` (quando entrambi presenti) → `400`.
3. `status` con valore fuori enum → `400`.
4. `ownerType` con valore fuori enum → `400`.
5. STANDARD + (`ownerId` o `ownerType`) → `400`.
6. `size > 200` → **clamp** a 200 (non errore). `size <= 0` → clamp al default 50.
   `page < 0` → clamp a 0.

Gli errori sono emessi come `application/problem+json` (RFC 9457) tramite il gestore
esistente.

## Architettura

Segue l'architettura esagonale del progetto e il pattern di `AuditController`.

```
VerificationJobListController (web, thin)
      → JobQueryService (application, business logic + scoping + validazione)
            → ValidationJobRepository (JpaSpecificationExecutor)
      → JobSummaryAssembler (Entity → JobSummary DTO)  [oppure mapping inline nel service]
```

### Componenti

1. **`VerificationJobListController`** — nuova classe dedicata (non un metodo dentro
   `AsyncVerificationController`, per tenere i file piccoli e la responsabilità singola).
   - `@RestController`, `@GetMapping("/api/v1/verifications/jobs")`.
   - Estrae il `Principal` dal `SecurityContext`, delega a `JobQueryService`, restituisce
     l'envelope.
   - Non applica `@PreAuthorize` a livello classe (l'endpoint è accessibile anche a
     STANDARD, con scoping); la distinzione ruolo è nel service.

2. **`JobQueryService`** (application) — logica isolata e testabile senza web layer:
   - Valida i parametri (regole 1–6).
   - Costruisce la `Specification<ValidationJob>` combinando: scoping per ruolo, owner,
     status `IN`, range date.
   - Normalizza la paginazione (clamp) e costruisce il `PageRequest` con sort fisso.
   - Ritorna un oggetto pagina (o direttamente `Page<ValidationJob>` più i metadati).

3. **`ValidationJobRepository`** — aggiungere `JpaSpecificationExecutor<ValidationJob>`:
   ```java
   public interface ValidationJobRepository
       extends JpaRepository<ValidationJob, UUID>,
               JpaSpecificationExecutor<ValidationJob> {
   ```

4. **DTO `JobSummary`** — generato dal contratto OpenAPI, riusa lo schema `PrincipalRef`
   già introdotto. L'assembly Entity→DTO avviene in un piccolo `JobSummaryAssembler` (o
   metodo privato nel service se resta banale). I controller non espongono entità.

### Specification (pseudo)

```java
Specification<ValidationJob> spec = (root, q, cb) -> {
  List<Predicate> ps = new ArrayList<>();
  // scoping
  if (actor.role() != PRIVILEGED) {
    ps.add(cb.equal(root.get("requestedByPrincipalType"), actor.type()));
    ps.add(cb.equal(root.get("requestedByPrincipalId"), actor.id()));
  } else {
    if (ownerId != null)   ps.add(cb.equal(root.get("requestedByPrincipalId"), ownerId));
    if (ownerType != null) ps.add(cb.equal(root.get("requestedByPrincipalType"), ownerType));
  }
  // status IN
  if (statuses != null && !statuses.isEmpty()) ps.add(root.get("status").in(statuses));
  // date range (createdOn già espanso a from/to nel service)
  if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
  if (to != null)   ps.add(cb.lessThan(root.get("createdAt"), to));
  return cb.and(ps.toArray(new Predicate[0]));
};
```

### Note

- **Nessuna scrittura**: la lista è read-only, non chiama `touchLastAccessed` né tocca
  `expires_at`/`@Version`. Nessun effetto collaterale sui worker in volo.
- **Nessuna migration DB necessaria per la correttezza**: si usa lo schema esistente di
  `validation_job` (`created_at` è già una colonna `NOT NULL`). Come ottimizzazione
  facoltativa, in fase di piano si valuterà un indice su
  `(requested_by_principal_id, created_at)` e/o `(created_at)` per la paginazione ordinata,
  eventualmente via migration `V8__*.sql`. Non è un prerequisito funzionale.
- **Audit**: coerentemente con `/audit-log` (che non auto-registra le proprie letture), la
  lista non genera eventi di audit per chiamata. Rivalutabile se serve tracciare gli
  accessi PRIVILEGED cross-owner.

## Contratto OpenAPI (design-first)

- Nuovo path `/api/v1/verifications/jobs` (GET) con i parametri sopra, risposte `200`,
  `400`, `401`.
- Nuovi schemi **tipizzati** (non `additionalProperties: true`):
  - `JobSummary` — riusa `$ref: PrincipalRef`.
  - `JobPage` — `{ page, size, totalElements, totalPages, content: [JobSummary] }`.
- `status` come array in query: `style: form, explode: true`, `items.enum` = stati.
- `OpenApiContractIT` protegge la conformità del contratto; i DTO vengono rigenerati.

## Testing

- **`JobQueryServiceTest`** (unit, no Spring): scoping STANDARD (forza self) vs PRIVILEGED
  (owner libero); ogni filtro (owner, status multiplo, `createdOn`, `from`/`to`);
  validazioni 1–6; clamp paginazione; sort DESC.
- **`VerificationJobListControllerTest`** (`@WebMvcTest` + mock service o `MockMvc`):
  STANDARD con `ownerId` → `400`; forma envelope e `JobSummary`; parsing `status` multiplo;
  `createdOn` vs `from/to`.
- **`OpenApiContractIT`** — conformità schema/contratto.
- Eventuale **IT** con Postgres (Testcontainers) per lo scoping reale su dati persistiti,
  se si vuole coprire la query end-to-end.

## Fuori scope

- Ordinamento configurabile (fisso `createdAt DESC`).
- Filtri diversi da owner/stato/data (es. per `phase`, `callbackUrl`).
- Cursor pagination (si usa offset page/size come `/audit-log`).
- Esposizione di `result`/errori nella lista.
- Audit per chiamata sulla lista.
