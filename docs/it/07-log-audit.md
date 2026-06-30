# 6. Log e audit

← [6. Estrazione file](06-estrazione-file.md) · [Indice](README.md)

L'osservabilità del servizio poggia su tre pilastri: **log applicativi
strutturati**, **metriche** (Actuator/Prometheus) e un **audit log** persistente
interrogabile via API.

```mermaid
flowchart LR
    APP[Applicazione] -->|JSON su STDOUT| LOG[Log strutturati\nlogback + MDC]
    APP -->|micrometer| MET[/actuator/prometheus/]
    APP --> DB[(audit_log)]
    DB -->|GET /api/v1/audit-log| ADM[Admin PRIVILEGED]
    LOG --> COLL[Collector\nLoki/ELK/...]
    MET --> PROM[Prometheus]

    classDef app fill:#eef1f5,stroke:#5b6b7c,color:#1f2733
    classDef store fill:#e1f5e9,stroke:#2f8a4e,color:#0d3a1d
    classDef client fill:#dbeeff,stroke:#2f6fbb,color:#0b2e4f
    classDef external fill:#fff1d6,stroke:#b9842a,color:#4a3203
    class APP app
    class DB store
    class ADM client
    class LOG,MET,COLL,PROM external
```

## 6.1 Log applicativi

I log sono emessi in **JSON** su STDOUT tramite Logback
(`logback-spring.xml`, encoder `LoggingEventCompositeJsonEncoder` di
logstash). Ogni evento include timestamp, livello, thread, logger, messaggio,
stacktrace, il campo `app` (nome applicazione) e il contenuto **MDC**.

Livelli di default (`application.yaml`):

| Logger | Livello |
|--------|---------|
| root | `INFO` |
| `org.toresoft.signverify` | `INFO` |
| `eu.europa.esig` (DSS) | `WARN` |

### Contesto per richiesta (MDC)

`RequestContextFilter` popola l'MDC a ogni richiesta, così ogni riga di log è
correlabile:

| Chiave MDC | Contenuto |
|------------|-----------|
| `requestId` | UUID generato per la richiesta |
| `clientIp` | indirizzo IP remoto |
| `principalType` | `API_KEY` / `OAUTH_USER` / `SYSTEM` (se autenticato) |
| `principalId` | id del principal (se autenticato) |

L'MDC viene **azzerato** al termine della richiesta (`MDC.clear()`).

### Rotazione

L'app scrive su STDOUT; la **rotazione** è demandata al runtime container. Nel
`docker-compose.prod.yml` il driver `json-file` ruota a `max-size: 10m` con
`max-file: 3`.

## 6.2 Gestione degli errori (problem+json)

Gli errori applicativi derivano da `AppException` e sono serializzati come
**RFC 9457** `application/problem+json` dal `GlobalExceptionHandler`. Il `type`
ha forma `urn:signverify:error:<codice>`.

| Codice errore | HTTP | Quando |
|---------------|------|--------|
| `validation.invalid-input` | 400 | input non valido (bean validation, JSON malformato, parametro errato) |
| `auth.invalid-token` | 401 | API key assente/malformata/sconosciuta/disabilitata/scaduta, o JWT non valido |
| `authz.forbidden` | 403 | ruolo insufficiente (es. `STANDARD` su endpoint `PRIVILEGED`) |
| `resource.not-found` | 404 | risorsa inesistente (o non visibile) |
| `resource.conflict` | 409 | conflitto (es. ultima chiave privilegiata) |
| `payload.too-large` | 413 | upload oltre i limiti |
| `signature.parse-error` | 422 | documento non firmato/illeggibile |
| `excessive-load.async-backpressure` | 429 | backpressure dei job asincroni |
| `excessive-load.concurrency` | 503 | semaforo di verifica sincrona esaurito |
| `tsl.not-ready` | 503 | Trusted Lists non ancora caricate |
| `dss.unavailable` | 503 | circuit breaker DSS aperto |
| `internal-error` | 500 | errore non previsto |

> **Riservati, non ancora emessi**: `validation.invalid-profile-overrides`,
> `auth.missing-credentials` e `media.unsupported` sono codici dichiarati ma
> senza un punto del codice che li sollevi oggi. Un header
> `X-API-Key`/`Authorization` assente emerge oggi comunque come
> `auth.invalid-token`; un valore di `profileOverrides` non valido finisce nel
> generico `internal-error` (500) invece che in un 400. È una lacuna nota, non
> ancora corretta.

Ogni risposta condivide la stessa busta; cambiano solo `type`, `status`,
`title` e `detail`:

```json
{
  "type": "urn:signverify:error:resource.conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "cannot remove last enabled privileged api key",
  "instance": "/api/v1/api-keys/3f1e..."
}
```

| Codice errore | Esempio `detail` |
|---------------|-------------------|
| `validation.invalid-input` | `"size must be between 1 and 100"` |
| `auth.invalid-token` | `"invalid credentials"` |
| `authz.forbidden` | `"insufficient role"` |
| `resource.not-found` | `"api key not found"` |
| `resource.conflict` | `"cannot remove last enabled privileged api key"` |
| `payload.too-large` | `"max upload size exceeded"` |
| `signature.parse-error` | `"cannot parse signed document: ..."` |
| `excessive-load.async-backpressure` | `"global async backpressure"` oppure `"per-principal async backpressure"` |
| `excessive-load.concurrency` | `"verify concurrency limit reached"` |
| `tsl.not-ready` | `"tsl not ready"` |
| `dss.unavailable` | `"dss circuit breaker open: ..."` |
| `internal-error` | `"unexpected error"` |

> Per default `server.error.include-message: never` e
> `include-stacktrace: never`: i dettagli interni non trapelano nelle risposte.

## 6.3 Audit log

Esiste una tabella **`audit_log`** e un'API di consultazione riservata agli
amministratori. Struttura del record (`AuditLog`):

| Campo | Descrizione |
|-------|-------------|
| `id` | UUID |
| `occurredAt` | istante dell'evento |
| `principalType` / `principalId` | autore (o `SYSTEM`) |
| `action` | azione (stringa) |
| `targetType` / `targetId` | risorsa interessata |
| `success` | esito booleano |
| `details` | JSON libero |
| `ipAddress` | IP del chiamante |

### Consultazione

`GET /api/v1/audit-log`: **richiede ruolo `PRIVILEGED`**. Restituisce la busta
di paginazione condivisa (vedi [Convenzioni](README.md#paginazione)). Filtri
disponibili:

| Parametro | Tipo |
|-----------|------|
| `principalId` | string |
| `action` | string |
| `from` / `to` | date-time |
| `targetType` / `targetId` | string |
| `success` | boolean |
| `page` / `size` | integer (default `0` / `50`) |

I risultati sono ordinati per `occurredAt` decrescente.

```bash
curl -sS "http://localhost:8080/api/v1/audit-log?action=verify&success=false&size=20" \
  -H "X-API-Key: $ADMIN_KEY"
```

```json
{
  "page": 0, "size": 20, "totalElements": 0, "totalPages": 0,
  "content": [ /* AuditLog[] */ ]
}
```

> **Stato attuale dell'implementazione.** La tabella `audit_log`, il
> componente `AuditService` (scrittura) e l'API di lettura sono presenti e
> indicizzati (`occurred_at`, `principal_id`, `action`). Nel codice corrente
> `AuditService` **non è ancora collegato** ai percorsi operativi (verifica,
> gestione chiavi, refresh TSL), quindi la tabella può risultare vuota: la
> tracciabilità operativa è oggi garantita dai **log strutturati** con MDC
> (§6.1). L'infrastruttura di audit persistente esiste già e può essere
> collegata a quelle operazioni quando serve.

## 6.4 Metriche

Endpoint Actuator esposti: `health`, `info`, `metrics`, `prometheus`.

- `GET /actuator/prometheus`: metriche in formato Prometheus (Micrometer),
  pubblico.
- Il circuit breaker `dssValidator` (Resilience4j) pubblica un health indicator
  ed espone metriche sullo stato (`CLOSED`/`OPEN`/`HALF_OPEN`), utili per
  monitorare la disponibilità della validazione DSS.

```mermaid
flowchart LR
    R4J[Resilience4j\ndssValidator] --> H[/actuator/health/]
    MM[Micrometer] --> P[/actuator/prometheus/]
    P --> PR[(Prometheus)]
    PR --> GR[Grafana]

    classDef app fill:#eef1f5,stroke:#5b6b7c,color:#1f2733
    classDef external fill:#fff1d6,stroke:#b9842a,color:#4a3203
    classDef store fill:#e1f5e9,stroke:#2f8a4e,color:#0d3a1d
    class R4J,MM app
    class H,GR external
    class P,PR store
```
