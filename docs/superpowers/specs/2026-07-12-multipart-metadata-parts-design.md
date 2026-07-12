# Design — Migrazione `metadata` JSON → part multipart dedicati

Data: 2026-07-12
Stato: approvato (brainstorming), pronto per il piano di implementazione.

## Contesto e problema

Oggi le due richieste di verifica firma accettano `multipart/form-data` con:

- `file` — il documento firmato (binary, obbligatorio);
- `metadata` — **un unico part stringa** che contiene un JSON serializzato con tutti i
  parametri extra.

Le chiavi di primo livello del JSON `metadata` sono:

| endpoint | chiavi metadata |
|---|---|
| `verifySync` (`POST /api/v1/verifications`) | `profileId`, `profileOverrides`, `reports` |
| `verifyAsync` (`POST /api/v1/verifications/async`) | `profileId`, `profileOverrides`, `reports`, `callbackUrl`, `callbackSecret`, `callbackAlgorithm` |

Riferimenti attuali:
- `src/main/java/org/toresoft/signverify/api/VerificationController.java:52-96`
- `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java:63-93`
- `src/main/resources/openapi/openapi.yaml:263-300`

Il JSON annidato in un solo campo è poco scopribile e poco auto-documentante: in
OpenAPI/Swagger UI non c'è schema per i singoli parametri, e i client devono costruire
JSON a mano. Vogliamo promuovere i parametri a **part multipart di primo livello**, pur
mantenendo `profileOverrides` come JSON perché è una struttura aperta/annidata.

`POST /api/v1/extractions` non ha `metadata` e resta **invariato**.

## Obiettivo

Esporre i parametri extra come part multipart dedicati, auto-documentati in OpenAPI,
mantenendo per una fase di transizione la compatibilità con il vecchio part `metadata`
(deprecato), in **mutua esclusività**.

## Contratto (openapi.yaml)

Nuovo schema `multipart/form-data` per `verifySync` e `verifyAsync`:

| part | tipo | sync | async | note |
|---|---|:--:|:--:|---|
| `file` | `string, format: binary` (required) | ✅ | ✅ | invariato |
| `profileId` | `string, format: uuid` | ✅ | ✅ | scalare piatto |
| `profileOverrides` | `string` (JSON serializzato) | ✅ | ✅ | resta JSON: struttura aperta |
| `reports` | `string` (CSV) | ✅ | ✅ | es. `simple,etsi`; valori: `simple|detailed|diagnostic|etsi` |
| `callbackUrl` | `string` | — | ✅ | piatto |
| `callbackSecret` | `string` | — | ✅ | piatto |
| `callbackAlgorithm` | `string` | — | ✅ | piatto |
| `metadata` | `string` (JSON) | ✅ | ✅ | **`deprecated: true`** — percorso legacy |

Solo `file` resta `required`. Tutti gli altri part sono opzionali. `OpenApiContractIT`
va aggiornato al nuovo schema.

Esempio d'uso (async, nuovo schema):

```
curl -F file=@doc.p7m \
     -F profileId=... \
     -F profileOverrides='{"...":"..."}' \
     -F reports=simple,etsi \
     -F callbackUrl=https://... \
     -F callbackSecret=... \
     -F callbackAlgorithm=HMAC-SHA256
```

## Modalità e mutua esclusività

Due modalità **mutuamente esclusive** — o `metadata` JSON (legacy, deprecato) **oppure**
i nuovi part, mai insieme:

- `metadata` presente **e** almeno uno dei nuovi part presente (`profileId`,
  `profileOverrides`, `reports`, `callbackUrl`, `callbackSecret`, `callbackAlgorithm`)
  → **400 Bad Request** via `AppException.badRequest`, emesso come
  `application/problem+json` (RFC 9457). Anche una compresenza *parziale* è errore.
- Solo `metadata` → percorso legacy (parsing del JSON come oggi).
- Solo nuovi part, o nessuno dei due → percorso nuovo (con i default per `reports`:
  `simple,etsi`).

Non esiste alcuna regola di precedenza campo-per-campo: la scelta della modalità è
tutto-o-niente e la compresenza è un errore di validazione esplicito.

## Componenti

### `VerificationRequestParts` (nuovo)

Piccola unità condivisa (un record + factory statica) che incapsula **la sola** logica di
risoluzione della modalità e di mutua esclusività. Vive nel package `api` accanto ai
controller.

Responsabilità:
- Ricevere i singoli `@RequestPart` opzionali + il `metadata` opzionale + il `JsonMapper`.
- Applicare la regola di mutua esclusività (compresenza → `AppException.badRequest`,
  *prima* di qualunque parsing di dettaglio).
- In modalità legacy: deserializzare `metadata` ed estrarre i campi.
- In modalità nuova: usare i part così come arrivano.
- Esporre valori grezzi normalizzati:
  `profileId (String)`, `profileOverridesJson (String)`, `reports (List<String>)`,
  `callbackUrl (String)`, `callbackSecret (String)`, `callbackAlgorithm (String)`.

Il CSV di `reports` viene splittato qui in `List<String>` (trim, scarto degli elementi
vuoti); la **mappatura a enum e la relativa severità sugli sconosciuti resta nei
controller**, invariata (vedi sotto).

Perché isolare: la regola vive in **un'unica unità testabile**, oggi duplicata/assente tra
i due controller, e i controller restano sottili.

### Controller (modifiche)

Le firme diventano:

```
verifySync(file, profileId?, profileOverrides?, reports?, metadata?)
verifyAsync(file, profileId?, profileOverrides?, reports?,
            callbackUrl?, callbackSecret?, callbackAlgorithm?, metadata?)
```

Ogni controller:
1. chiama `VerificationRequestParts.from(...)` per ottenere i valori grezzi risolti;
2. applica il **mapping downstream invariato**:
   - `verifySync`: costruisce il record `Metadata` e usa `parseReports` **strict**
     (uno `reports` sconosciuto → `AppException.badRequest`), come oggi
     (`VerificationController.java:98-111`);
   - `verifyAsync`: mapping **lenient** con silent-ignore degli sconosciuti, come oggi
     (`AsyncVerificationController.java:83-90`).

Nessun cambiamento di semantica sugli errori di mapping dei report: si preserva il
comportamento attuale di ciascun endpoint.

## Gestione errori

- Compresenza `metadata` + nuovi part → 400 `application/problem+json`.
- `profileOverrides` / `metadata` JSON malformato → 400 (come oggi).
- `reports` sconosciuto → dipende dall'endpoint (sync strict 400 / async lenient), invariato.

## Testing

- **Unit `VerificationRequestPartsTest`** (nuovo):
  - solo nuovi part → valori risolti correttamente;
  - solo `metadata` legacy → valori risolti dal JSON;
  - compresenza (anche parziale, es. `metadata` + solo `reports`) → `AppException` 400;
  - nessuno dei due → default (`reports` = `simple,etsi`, resto null);
  - CSV `reports` con spazi/elementi vuoti → normalizzazione.
- **`VerificationControllerTest`** / **`AsyncVerificationControllerTest`**: aggiornati per
  i nuovi part; aggiunto un caso legacy `metadata` e un caso **"mix → 400"** per entrambi.
- **`OpenApiContractIT`**: aggiornato al nuovo schema.
- `mvn verify` (Spotless GJF + ArchUnit + Failsafe IT + JaCoCo) verde prima del commit.

## Fuori scope

- `POST /api/v1/extractions` (nessun `metadata`).
- Rimozione definitiva del part `metadata` (avverrà in un follow-up dopo la migrazione
  dei client).
- Unificazione della severità del mapping `reports` tra sync e async (comportamento
  attuale preservato).