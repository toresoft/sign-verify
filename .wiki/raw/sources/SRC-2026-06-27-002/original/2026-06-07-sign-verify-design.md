# sign-verify-2 — Design Document

> Servizio REST per la verifica della validità di firme elettroniche su documenti, con funzionalità accessoria di estrazione del documento originale da formati di firma che wrappano il file firmato.

| Campo | Valore |
|---|---|
| Versione documento | 1.0 |
| Data | 2026-06-07 |
| Stato | Bozza per approvazione |
| Lingua | Italiano (commit/codice in inglese) |
| Repository | `org.toresoft:sign-verify-2` |

---

## 1. Scope e obiettivi

### 1.1 Scope incluso

- **Verifica firma**: validazione di firme elettroniche su documenti con report dettagliati conformi a standard ETSI.
- **Estrazione documento originale**: ottenere il payload non firmato da formati che wrappano (CAdES enveloping `.p7m`, ASiC-S/E, XAdES enveloping).
- **Gestione profili di verifica**: preset e XML custom DSS, con possibilità di override puntuale per singola richiesta.
- **Gestione trust list (TSL/LOTL)**: download, refresh automatico/manuale, mirror DB queryable.
- **Autenticazione duale**: API key (consumatori applicativi) + OAuth2/OIDC (utilizzatori umani via IdP esterno) intercambiabili.
- **Autorizzazione role-based**: ruoli `PRIVILEGED` e `STANDARD` sul principal.
- **Audit log** delle operazioni rilevanti.
- **Modalità asincrona** con callback HMAC + recupero esplicito via GET.

### 1.2 Scope escluso

- Generazione/creazione di firme (solo verifica + estrazione).
- Interfaccia utente: il servizio non eroga UI per esseri umani. Tutte le funzionalità sono REST.
- Rate limiting per principal in token-bucket (non richiesto v1).
- Multi-tenancy avanzata (eventuale futura estensione).

### 1.3 Target tecnologico

- **Java 21 LTS**
- **Spring Boot 3.4.x**
- **DSS 6.4** (`eu.europa.ec.joinup.sd-dss`) — libreria EU per firma elettronica
- **JPA/Hibernate** astratto su RDBMS via JDBC, H2 in dev, configurabile in produzione (Postgres consigliato)
- **Flyway** per migrazioni DB
- **Resilience4j** per circuit breaker
- **ShedLock** per coordinamento scheduler in multi-istanza
- **Micrometer/Prometheus** per metriche
- **OpenAPI 3** design-first con `openapi-generator-maven-plugin`

---

## 2. Glossario

| Termine | Significato |
|---|---|
| DSS | Digital Signature Services — libreria EU per firma elettronica |
| TSL | Trusted List (lista di certificati di TSP fidati per stato membro) |
| LOTL | List of Trusted Lists (lista di TSL gestita dalla Commissione UE) |
| TSP | Trust Service Provider |
| SKI | Subject Key Identifier |
| AKI | Authority Key Identifier |
| OJ | Official Journal (keystore con certificati di firma del LOTL) |
| Indication / SubIndication | Esito qualificato della verifica firma (ETSI TS 119 102-1) |
| ETSI TS 119 102-2 | Standard ETSI per il formato del report di validazione firma |
| Principal | Identità autenticata (API key o OAuth user) |
| Profilo | Set di regole di validazione DSS (constraint XML) |

---

## 3. Architettura

### 3.1 Stile architetturale

Approccio **modulo Maven singolo + layered + hexagonal-lite per le integrazioni esterne**. Le integrazioni che hanno alto rischio o complessità (DSS, OAuth IdP, callback HTTP, filesystem, password hashing) sono dietro **porte di dominio** con **adapter** come implementazioni. Il resto del codice segue un layering classico Spring.

Razionale: bilancia velocità di sviluppo (struttura familiare a chiunque conosca Spring Boot) con testabilità degli adapter critici (DSS è pesante e con TSL/cert reali è difficile da invocare in unit test).

### 3.2 Diagramma componenti

```
┌─────────────────────────────────────────────────────────────────┐
│                       sign-verify-2 (Spring Boot)                │
├─────────────────────────────────────────────────────────────────┤
│  REST API (controller, DTO generati da openapi.yaml)             │
├─────────────────────────────────────────────────────────────────┤
│  Application Services (business logic, orchestrazione)           │
│  ├─ VerificationService     ├─ ProfileService                    │
│  ├─ ExtractionService       ├─ ApiKeyService                     │
│  ├─ AsyncJobService         ├─ TslService                        │
│  └─ AuditService                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Domain (entity JPA + porte)                                     │
│  Porte: SignatureValidatorPort, ExtractionPort,                  │
│         TslRefresherPort, CallbackDispatcherPort,                │
│         DocumentStoragePort, PasswordHasherPort,                 │
│         SecretCipherPort                                         │
├─────────────────────────────────────────────────────────────────┤
│  Adapter (implementazioni porte)                                 │
│  ├─ DssValidatorAdapter     ├─ DssExtractionAdapter              │
│  ├─ DssTslAdapter (TLValidationJob)                              │
│  ├─ FilesystemDocumentStorageAdapter                             │
│  ├─ HmacCallbackDispatcherAdapter                                │
│  ├─ BcryptPasswordHasherAdapter                                  │
│  └─ AesGcmSecretCipherAdapter                                    │
├─────────────────────────────────────────────────────────────────┤
│  Persistence (Spring Data JPA repository)                        │
├─────────────────────────────────────────────────────────────────┤
│  Infra: Spring Security (API key filter + OAuth Resource Server) │
└─────────────────────────────────────────────────────────────────┘
              │                              │
              ▼                              ▼
         [RDBMS via JDBC]            [Filesystem job storage]
              │
              ▼
    [EU LOTL + TSL URLs esterni]
```

### 3.3 Struttura package

```
org.toresoft.signverify
├── api                      # controller REST + mapping da/verso DTO generati
├── application              # service applicativi (use case)
├── domain
│   ├── model                # entity JPA, value object
│   ├── port                 # interfacce per integrazioni esterne
│   └── exception            # eccezioni di dominio
├── adapter
│   ├── dss                  # DSS validator/extractor/TSL
│   ├── storage              # filesystem (impl port)
│   ├── callback             # HMAC dispatcher (impl port)
│   └── crypto               # bcrypt hasher, AES-GCM cipher (impl port)
├── persistence              # repository Spring Data JPA
├── security                 # filter API key, principal unificato, role resolver
├── config                   # bean config, DSS beans, async pool, scheduler
└── SignVerifyApplication.java
```

### 3.4 Vincoli architetturali (enforced da ArchUnit)

- Package `domain` non importa `org.springframework.*`, `eu.europa.esig.*`, `org.hibernate.*` (eccezione: `jakarta.persistence.*` per le entity).
- Package `adapter.dss` è l'unico ad importare `eu.europa.esig.*`.
- Controller non chiama mai repository direttamente: sempre via service.
- DTO generati (package `api.dto`) usati solo da controller, mai dal dominio.

---

## 4. Modello dati

### 4.1 Tabelle principali

#### `api_key`
| Colonna | Tipo | Note |
|---|---|---|
| `id` | UUID PK | |
| `name` | varchar(120) UNIQUE | identificatore leggibile |
| `key_prefix` | varchar(8) | primi 8 char plaintext (per lookup veloce + log) |
| `key_hash` | varchar(255) | bcrypt cost 12 |
| `role` | varchar(20) | `PRIVILEGED` \| `STANDARD` |
| `enabled` | bool | |
| `bootstrap` | bool | flag della seed key auto-generata |
| `expires_at` | timestamp NULL | |
| `created_at` | timestamp | |
| `created_by_principal_type` | varchar(20) | |
| `created_by_principal_id` | varchar(120) | |
| `last_used_at` | timestamp NULL | |

Indici: `(role, enabled)` per il check del vincolo "ultima privilegiata".

#### `verification_profile`
| Colonna | Tipo | Note |
|---|---|---|
| `id` | UUID PK | |
| `name` | varchar(120) UNIQUE | |
| `description` | text | |
| `preset` | varchar(20) | `BASIC` \| `STANDARD` \| `STRICT` \| `CUSTOM` |
| `policy_xml` | text | DSS constraint XML materializzato anche per preset |
| `is_default` | bool | unique partial index `WHERE is_default=true` (un solo default) |
| `version` | bigint | optimistic locking |
| `created_at`, `updated_at` | timestamp | |

#### `validation_job`
| Colonna | Tipo | Note |
|---|---|---|
| `id` | UUID PK | |
| `status` | varchar(20) | `PENDING`/`RUNNING`/`COMPLETED`/`FAILED`/`DELIVERED`/`DELIVERY_FAILED`/`DELETED` |
| `original_status` | varchar(20) NULL | valore di `status` prima della tombstone (riempito quando `status=DELETED`) |
| `profile_id` | UUID FK NULL | |
| `profile_overrides` | text NULL | JSON override puntuale |
| `reports_requested` | varchar(100) | csv tipi report (es. `simple,etsi`) |
| `document_path` | varchar(500) NULL | path filesystem doc input (nullable: cancellato presto) |
| `document_filename` | varchar(255) | nome originale |
| `result_path` | varchar(500) NULL | path JSON result |
| `callback_url` | varchar(500) NULL | nullable: callback opzionale |
| `callback_secret_cipher` | text NULL | cifrato AES-GCM (master key da env) |
| `callback_algorithm` | varchar(20) | default `HmacSHA256` |
| `callback_attempts` | int | |
| `next_callback_at` | timestamp NULL | scheduling retry |
| `last_callback_error` | text NULL | sintesi ultimo errore |
| `pickup_attempts` | int | contatore tentativi di pickup quando CB DSS aperto |
| `created_at`, `started_at`, `completed_at`, `delivered_at` | timestamp | |
| `expires_at` | timestamp | TTL per `PENDING`/`RUNNING` |
| `deleted_at` | timestamp NULL | valorizzato quando entra in tombstone |
| `error_message` | text NULL | |
| `requested_by_principal_type` | varchar(20) | `API_KEY` \| `OAUTH_USER` |
| `requested_by_principal_id` | varchar(120) | per ownership |
| `last_accessed_at` | timestamp NULL | aggiornato su GET, utile per metriche |

Indici:
- `(status, next_callback_at)` per polling worker callback
- `(status, pickup_attempts)` per polling worker validazione
- `(status, expires_at)` per cleanup expired
- `(requested_by_principal_type, requested_by_principal_id, status)` per filtro ownership

#### `trusted_certificate` (mirror queryable post-refresh TSL)
| Colonna | Tipo | Note |
|---|---|---|
| `id` | UUID PK | |
| `fingerprint_sha256` | varchar(64) UNIQUE | dedup |
| `ski` | varchar(64) | indexed |
| `aki` | varchar(64) | indexed |
| `subject_dn` | varchar(500) | indexed |
| `subject_cn` | varchar(255) | indexed |
| `issuer_dn` | varchar(500) | indexed |
| `issuer_cn` | varchar(255) | |
| `serial_number` | varchar(80) | indexed |
| `country` | varchar(8) | indexed |
| `tsp_name` | varchar(255) | indexed |
| `tsp_service_type` | varchar(255) | indexed |
| `tsp_service_status` | varchar(80) | indexed |
| `valid_from`, `valid_to` | timestamp | indexed |
| `certificate_der_b64` | text | per recover/export |
| `tsl_url` | varchar(500) | sorgente TSL |
| `last_seen_at` | timestamp | aggiornato ad ogni refresh in cui il cert è presente |
| `removed_at` | timestamp NULL | soft-delete: cert non più presente in TSL |

#### `tsl_refresh` (storico refresh)
| Colonna | Tipo | Note |
|---|---|---|
| `id` | UUID PK | |
| `trigger` | varchar(20) | `SCHEDULED` \| `MANUAL` \| `STARTUP` |
| `triggered_by_principal_type` | varchar(20) NULL | |
| `triggered_by_principal_id` | varchar(120) NULL | |
| `started_at`, `completed_at` | timestamp | |
| `status` | varchar(20) | `RUNNING` \| `SUCCESS` \| `PARTIAL` \| `FAILED` |
| `sources_total`, `sources_failed` | int | |
| `certificates_added`, `certificates_removed`, `certificates_unchanged` | int | |
| `error_summary` | text NULL | |

#### `audit_log` (append-only)
| Colonna | Tipo | Note |
|---|---|---|
| `id` | UUID PK | |
| `occurred_at` | timestamp | indexed |
| `principal_type` | varchar(20) | `API_KEY` \| `OAUTH_USER` \| `SYSTEM` |
| `principal_id` | varchar(120) | indexed |
| `action` | varchar(60) | indexed |
| `target_type` | varchar(40) NULL | |
| `target_id` | varchar(120) NULL | |
| `success` | bool | |
| `details` | text | JSON contesto |
| `ip_address` | varchar(64) NULL | |

#### `shedlock` (gestito da ShedLock library)
Tabella standard ShedLock per lock distribuiti scheduler.

### 4.2 Invarianti applicative

| Regola | Enforce |
|---|---|
| ≥ 1 API key `role=PRIVILEGED AND enabled=true` sempre presente | Service layer transazionale: `SELECT COUNT(*) ... FOR UPDATE` pre delete/disable; conflict 409 se ultima |
| Esattamente 1 profilo `is_default=true` | Partial unique index su Postgres; transazione applicativa cross-DB |
| Job non eseguito se documento di input mancante | Service controlla esistenza file all'inizio del `processJob`; se assente → FAILED |
| Cleanup file orfani | Scheduler verifica path su FS senza match in DB |

### 4.3 Migrazioni

- **Flyway** versioned: `V1__init.sql`, `V2__seed_default_profile.sql`, …
- SQL scritto in subset compatibile H2 + Postgres. Costrutti dialetto-specifici (es. partial unique index Postgres) in migration `V{n}__postgres_*.sql` esclusa dal classpath H2 via callback Flyway o profilo Spring.
- Profilo `STANDARD` viene caricato come default al primo avvio se la tabella `verification_profile` è vuota.

---

## 5. API REST

### 5.1 Versioning e formati

- Base path: `/api/v1/*`
- Versionamento dal path
- Body JSON salvo upload multipart
- Errori in `application/problem+json` (RFC 9457)
- OpenAPI 3 sorgente autoritativa: `src/main/resources/openapi/openapi.yaml` (design-first)
- Codici errore stabili tramite `type` URN `urn:signverify:error:<code>` (catalogo in §11)

### 5.2 Endpoint

#### 5.2.1 Verifica firma

| Metodo | Path | Auth | Descrizione |
|---|---|---|---|
| POST | `/api/v1/verifications` | qualsiasi | Verifica sincrona |
| POST | `/api/v1/verifications/async` | qualsiasi | Verifica asincrona |
| GET | `/api/v1/verifications/jobs/{jobId}` | owner o `PRIVILEGED` | Stato + risultato job async |

**Sync request** (multipart):
- `file` (binary) — documento firmato
- `metadata` (json part):
  ```json
  {
    "profileId": "uuid?",
    "profileOverrides": { /* vedi §6.2 */ },
    "reports": ["simple", "etsi"]
  }
  ```

**Async request**: come sync + `metadata` include:
```json
{
  "callbackUrl": "https://client.example/cb",
  "callbackSecret": "...",
  "callbackAlgorithm": "HmacSHA256"
}
```
`callbackUrl` opzionale: se assente, il client può solo fare polling via GET.

**Sync response** (200):
```json
{
  "verifiedAt": "2026-06-07T10:23:11Z",
  "profileUsed": "STANDARD",
  "overridesApplied": false,
  "signatureFormat": "PAdES-LTA",
  "indication": "TOTAL_PASSED",
  "subIndication": null,
  "signatureCount": 1,
  "reports": {
    "simple": { ... },
    "etsi": { ... }
  },
  "warnings": [],
  "errors": []
}
```

**Async response** (202):
```http
202 Accepted
Location: /api/v1/verifications/jobs/<uuid>

{
  "jobId": "<uuid>",
  "status": "PENDING",
  "expiresAt": "..."
}
```

**GET job response per stato**:

| Stato | HTTP | Body |
|---|---|---|
| `PENDING`/`RUNNING` | 200 | `{jobId, status, createdAt, startedAt?, expiresAt}` |
| `COMPLETED` | 200 | full sync response + `{jobId, status, completedAt}` |
| `FAILED` | 200 | `{jobId, status, error, completedAt}` |
| `DELIVERED` | 200 | full result + `{deliveredAt, callbackAttempts}` |
| `DELIVERY_FAILED` | 200 | full result + `{lastCallbackError, callbackAttempts}` |
| `DELETED` (tombstone), richiesta da owner o `PRIVILEGED` | 410 | `{jobId, originalStatus, completedAt, deletedAt, message}` |
| `DELETED` (tombstone), richiesta da non-owner non-priv | 404 | regola ownership (§5.4) prevale: non si leaka l'esistenza |
| ID inesistente o post-tombstone | 404 | problem detail |

GET aggiorna `last_accessed_at` (asincrono, non transazionale con la read).

#### 5.2.2 Estrazione documento originale

| Metodo | Path | Auth | Descrizione |
|---|---|---|---|
| POST | `/api/v1/extractions` | qualsiasi | Estrai documento da firma wrappante |

Multipart con `file`. Risposta:
- 1 documento → binary diretto con `Content-Type` originale rilevato.
- N documenti (ASiC-E) → `application/zip` con tutti i file originali.

Header response:
- `X-Signature-Format: PAdES-B-B` (o XAdES/CAdES/ASiC-S/E ecc.)
- `X-Document-Count: <n>`

#### 5.2.3 Profili

| Metodo | Path | Auth |
|---|---|---|
| GET | `/api/v1/profiles` | qualsiasi (paginato) |
| GET | `/api/v1/profiles/{id}` | qualsiasi |
| POST | `/api/v1/profiles` | `PRIVILEGED` |
| PUT | `/api/v1/profiles/{id}` | `PRIVILEGED` |
| DELETE | `/api/v1/profiles/{id}` | `PRIVILEGED` (409 se default) |
| POST | `/api/v1/profiles/{id}/default` | `PRIVILEGED` |

Body create/update:
```json
{
  "name": "STRICT-IT",
  "description": "...",
  "preset": "CUSTOM",
  "policyXml": "<ConstraintsParameters>...</ConstraintsParameters>"
}
```
Validazione: `policyXml` validato contro XSD DSS prima del save.

#### 5.2.4 API key

| Metodo | Path | Auth |
|---|---|---|
| GET | `/api/v1/api-keys` | `PRIVILEGED` |
| POST | `/api/v1/api-keys` | `PRIVILEGED` |
| PATCH | `/api/v1/api-keys/{id}` | `PRIVILEGED` |
| DELETE | `/api/v1/api-keys/{id}` | `PRIVILEGED` |

Create response include il plaintext **una sola volta**:
```json
{
  "id": "uuid",
  "name": "...",
  "role": "STANDARD",
  "plaintextKey": "sv_xxxxxxxx_<rest>",
  "createdAt": "..."
}
```
Successivo GET non ritorna mai plaintext: solo `keyPrefix`.

DELETE/PATCH che disabilita ultima `PRIVILEGED enabled` → 409 `resource.conflict`.

#### 5.2.5 TSL

| Metodo | Path | Auth | Descrizione |
|---|---|---|---|
| GET | `/api/v1/tsl/status` | qualsiasi | stato refresh + readiness |
| POST | `/api/v1/tsl/refresh` | `PRIVILEGED` | force-refresh (async, 202) |
| GET | `/api/v1/tsl/certificates` | qualsiasi | lista paginata con filtri |
| GET | `/api/v1/tsl/certificates/{id}` | qualsiasi | dettaglio + DER base64 |

Filtri querystring su `/tsl/certificates`:
`ski`, `aki`, `subjectCn`, `subjectDn`, `issuerCn`, `issuerDn`, `country`, `tspName`, `tspServiceType`, `tspServiceStatus`, `serialNumber`, `validAt` (date), `includeRemoved` (bool, default false), `page`, `size`, `sort`.

#### 5.2.6 Audit

| Metodo | Path | Auth | Descrizione |
|---|---|---|---|
| GET | `/api/v1/audit-log` | `PRIVILEGED` | paginato, filtri |

Filtri: `principalId`, `action`, `from`, `to`, `targetType`, `targetId`, `success`.

### 5.3 Autenticazione

#### 5.3.1 Filter chain

Ordine in `SecurityFilterChain`:
1. `ApiKeyAuthenticationFilter` — header `X-API-Key`. Hash lookup + check `enabled` + `expires_at`. Popola `ApiKeyAuthenticationToken`.
2. **OAuth2 Resource Server (JWT)** — header `Authorization: Bearer <jwt>`. `issuer-uri` da property, JWKS validation.

Se nessuna credenziale → `401 auth.missing-credentials`.
Se entrambe presenti → API key prevale, logga warning.

#### 5.3.2 Principal unificato

```java
public record Principal(
    PrincipalType type,    // API_KEY | OAUTH_USER | SYSTEM
    String id,
    Role role,             // PRIVILEGED | STANDARD
    String displayName
) {}
```

Iniettato nei controller via `@AuthenticationPrincipal`.

Per OAuth: `role` derivato da claim configurabile (default `roles` o `scope`). Mapping property `app.security.oauth.role-claim` + `app.security.oauth.privileged-values`.

#### 5.3.3 Bootstrap API key seed

Al primo avvio (transazione di startup), se nessuna `api_key` con `role=PRIVILEGED AND enabled=true` esiste:
1. Genera plaintext random crypto-secure (`sv_<8 prefix>_<48 char base32>`)
2. Hash bcrypt cost 12, persiste con `bootstrap=true`
3. Scrive plaintext **una sola volta** in `${app.security.bootstrap-key-file}` (default `/var/lib/sign-verify/bootstrap-api-key.txt`, permessi 600 verificati a write-time)
4. Logga warning critico: `BOOTSTRAP API KEY generated. File: <path>. Delete after pickup.`

L'operatore di deploy preleva il file, lo cancella, usa la chiave per creare altre key normali e poi disabilita o cancella la bootstrap key.

### 5.4 Autorizzazione

- Method-level `@PreAuthorize("hasRole('PRIVILEGED')")` su endpoint admin (gestione API key, profili create/update/delete/default, force-refresh TSL, audit).
- **Ownership** su `/verifications/jobs/{id}` (GET):
  - `PRIVILEGED` accede a qualsiasi job
  - `STANDARD` accede solo a job dove `requested_by_principal_type` AND `requested_by_principal_id` matchano esattamente il principal corrente
  - Non-owner non-privileged → **404** (non 403, per non leakare esistenza)
- Modello ownership stretto: API key e OAuth user sono identità distinte. Stesso operatore umano che sottomette via API key non recupererà via OAuth e viceversa, salvo essere `PRIVILEGED`.

### 5.5 Limiti payload

- Max upload size 50 MB (configurabile `app.upload.max-size`).
- Rifiuto > limit → `413 payload.too-large`.

---

## 6. Engine verifica firma

### 6.1 Configurazione DSS

```java
@Configuration
class DssConfiguration {
    @Bean
    CertificateVerifier certificateVerifier(TrustedListsCertificateSource tslSource) {
        var cv = new CommonCertificateVerifier();
        cv.setTrustedCertSources(tslSource);
        cv.setAIASource(new DefaultAIASource());
        cv.setOcspSource(new OnlineOCSPSource());
        cv.setCrlSource(new OnlineCRLSource());
        cv.setRevocationFallback(true);
        return cv;
    }
}
```

### 6.2 Profili: preset + custom + override puntuali

#### Preset built-in
Inseriti come righe in `verification_profile` al boot se mancanti. XML materializzato da risorse classpath:

- `BASIC` — solo signature integrity + cert chain (skip revocation, skip timestamp validation)
- `STANDARD` — integrity + chain + revocation (OCSP/CRL), basato sul file `constraint.xml` di riferimento DSS (modulo `dss-validation`, materializzato a build-time in `resources/policy/STANDARD.xml`)
- `STRICT` — STANDARD + qualified certificate + AdES-T/LT/LTA timestamps + algorithm policy stringente

#### Custom
Admin POST `policyXml` arbitrario. Validato contro XSD DSS (`policy-constraints.xsd`) prima del persist.

#### Override puntuali per singola richiesta

Campo `profileOverrides` in request:
```json
{
  "checkRevocation": false,
  "checkSignatureIntegrity": true,
  "checkCertificateChain": true,
  "checkTimestamp": false,
  "checkQualified": false,
  "allowedSignatureAlgorithms": ["RSA-SHA256", "RSA-SHA512", "ECDSA-SHA256"],
  "allowedDigestAlgorithms": ["SHA256", "SHA384", "SHA512"]
}
```

Applicazione:
1. Carica `profile.policyXml` (o default profile se `profileId` null)
2. Parser DOM applica gli override:
   - `checkXxx=false` → setta `Level="IGNORE"` sul nodo corrispondente
   - `allowedSignatureAlgorithms` → sostituisce `<AcceptableEncryptionAlgo><Algo>` list
   - `allowedDigestAlgorithms` → analogo per `<AcceptableDigestAlgo>`
3. XML risultante passato a `ValidationPolicyLoader.fromValidationPolicy(...).create()`

Override **mai persistiti**, applicati solo a quella verifica. Schema documentato in OpenAPI con Bean Validation.

### 6.3 Caching policy parsing

`Caffeine<String policyId, ValidationPolicy>` per evitare re-parse XML su ogni verifica. Invalidato via listener JPA `@PostUpdate`/`@PostRemove` su `verification_profile`.

Override puntuali bypassano la cache (XML è derivato e unico per request).

### 6.4 Esecuzione verifica

```
DssValidatorAdapter.validate(file, policyXml, reportsRequested):
  1. doc = new InMemoryDocument(bytes, filename)
  2. validator = SignedDocumentValidator.fromDocument(doc)
  3. validator.setCertificateVerifier(certificateVerifier)
  4. policy = ValidationPolicyLoader.fromValidationPolicy(...).create()
  5. reports = validator.validateDocument(policy)
  6. ritorna mappa filtrata dei report richiesti
```

### 6.5 Report disponibili

| Tipo | Sorgente DSS | Formato JSON |
|---|---|---|
| `simple` | `reports.getSimpleReport()` | JSON via DSS facade |
| `detailed` | `reports.getDetailedReport()` | JSON |
| `diagnostic` | `reports.getDiagnosticData()` | JSON |
| `etsi` | `reports.getEtsiValidationReportJaxb()` (ETSI TS 119 102-2) | JSON |

Default in request se `reports` non specificato: `["simple", "etsi"]`.

`indication` e `subIndication` da SimpleReport sono **sempre** valorizzati nella response top-level anche se `simple` non è richiesto fra i report, per dare accesso quick.

### 6.6 Estrazione documento

```
DssExtractionAdapter.extract(file):
  validator = SignedDocumentValidator.fromDocument(...)
  validator.setCertificateVerifier(certificateVerifier)   // light context
  List<DSSDocument> originals = validator.getOriginalDocuments()
  if (originals.size() == 1) → binary singolo con Content-Type da DSSDocument.getMimeType()
  else → ZIP con tutti i file originali
```

---

## 7. TSL management

### 7.1 Configurazione

```yaml
app:
  dss:
    cache-dir: /var/lib/sign-verify/dss-cache
    online-mode: true
  tsl:
    sources:
      - id: eu-lotl
        type: LOTL
        url: https://ec.europa.eu/tools/lotl/eu-lotl.xml
        pivot-support: true
        oj-keystore-path: classpath:keystore/oj-keystore.p12
        oj-keystore-password-env: APP_OJ_KEYSTORE_PASSWORD
        oj-url: https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=uriserv:OJ.C_.2019.276.01.0001.01.ENG
    refresh:
      cron: "0 0 2 * * *"
      timezone: Europe/Rome
      startup-mode: BACKGROUND     # BACKGROUND | BLOCKING | SKIP
```

`type: LOTL` o `type: TL`. Sources è una lista: si possono configurare più LOTL o TSL specifici. Lista vuota → fallback hardcoded su `eu-lotl`.

### 7.2 OJ keystore

DSS richiede il keystore con certificati del Journal Officiel per verificare la firma del LOTL. Strategia:
- File `resources/keystore/oj-keystore.p12` bundlato nell'immagine
- Aggiornato manualmente quando viene pubblicata una nuova OJ
- Password da env var (`oj-keystore-password-env`)

### 7.3 Bootstrap startup

| Modalità | Comportamento |
|---|---|
| `BLOCKING` | `TLValidationJob.onlineRefresh()` chiamato in `ApplicationReadyEvent`. App non serve richieste finché trust source vuota. Startup 60-180s. |
| `BACKGROUND` (default) | Refresh schedulato subito dopo avvio in thread separato. App già up. Verifiche fallano `503 tsl.not-ready` finché completato. Stato esposto da `/tsl/status`. |
| `SKIP` | Usa solo cache filesystem persistente se presente. Refresh solo via cron/force. Per ambienti offline. |

`/actuator/health/readiness` riflette readiness reale.

### 7.4 Refresh atomico (hot swap)

DSS è già progettato per zero-downtime:
- `TLValidationJob.onlineRefresh()` scarica nuovi TSL in cache filesystem
- Solo dopo successo, popola nuovo snapshot di `TrustedListsCertificateSource` interno
- `CertificateVerifier` referenzia il bean Spring `TrustedListsCertificateSource`, DSS swappa atomicamente field internal
- Richieste in-flight usano snapshot vecchio, successive il nuovo

### 7.5 Scheduler refresh + ShedLock

```java
@Component
class TslRefreshScheduler {
    @Scheduled(cron = "${app.tsl.refresh.cron}", zone = "${app.tsl.refresh.timezone}")
    @SchedulerLock(name = "tslRefresh", lockAtMostFor = "30m", lockAtLeastFor = "5m")
    void scheduledRefresh() {
        tslService.refresh(RefreshTrigger.SCHEDULED, null);
    }
}
```

### 7.6 Force-refresh

`POST /api/v1/tsl/refresh`:
- Crea row `tsl_refresh` `status=RUNNING`, `trigger=MANUAL`
- Submette task su pool dedicato size 1
- Return immediato `202` con `{refreshId}`
- Se refresh già in corso (ShedLock occupato) → `409` con `refreshId` corrente

### 7.7 Mirror sync algorithm

```
Post-refresh listener:
  currentByFp = map fingerprint -> X509Certificate (da TrustedListsCertificateSource)
  dbByFp = map fingerprint -> entity (WHERE removed_at IS NULL)

  toAdd    = currentByFp.keys - dbByFp.keys
  toRemove = dbByFp.keys - currentByFp.keys
  toUpdate = currentByFp.keys ∩ dbByFp.keys

  in transazione:
    insert toAdd (estrai metadata: SKI, AKI, subject_*, issuer_*, country, TSP info)
    update toUpdate set last_seen_at = now(), tsp_service_status (refresh)
    update toRemove set removed_at = now()
    insert tsl_refresh row con statistiche
```

Metadata TSP estratti via `TrustedListsCertificateSource.getTrustServices(cert)`.
Country dal certificato `Subject` (campo C) o dalla scheme territory del TSL parent.

### 7.8 Stato refresh API

Esempio response `GET /api/v1/tsl/status`:
```json
{
  "lastRefresh": {
    "id": "...",
    "trigger": "SCHEDULED",
    "startedAt": "...",
    "completedAt": "...",
    "status": "SUCCESS",
    "sourcesTotal": 31,
    "sourcesFailed": 0,
    "certificatesAdded": 12,
    "certificatesRemoved": 3,
    "certificatesUnchanged": 1845
  },
  "nextScheduledRefresh": "2026-06-08T02:00:00+02:00",
  "currentCertificateCount": 1857,
  "tslSources": [
    { "id": "eu-lotl", "url": "...", "lastDownloadedAt": "...", "status": "OK" }
  ],
  "ready": true
}
```

---

## 8. Async job + callback HMAC

### 8.1 State machine

```
Submit:    POST async ──► PENDING

Worker validazione (poll):
  PENDING ──pickup, status=RUNNING──► RUNNING

  RUNNING ──DSS ok──────────────────► COMPLETED
  RUNNING ──errore non-recoverable──► FAILED
  RUNNING ──CB open, attempts<max───► PENDING (pickup_attempts++)
  RUNNING ──CB open, attempts>=max──► FAILED  (error=dss_unavailable_after_max_attempts)
  
  Expired: PENDING/RUNNING + expires_at<now ──► FAILED (error=job_expired)

Worker callback (se callback_url != null):
  COMPLETED|FAILED ──post HMAC ok──► DELIVERED
  COMPLETED|FAILED ──retryable n×──► (riprogramma con backoff)
  COMPLETED|FAILED ──non-retryable o attempts>=max──► DELIVERY_FAILED

Cleanup scheduler (giornaliero):
  fase 1 input:     terminal state + completed_at < now - input-retention
                    → cancella file documento input
  fase 2 tombstone: terminal state + completed_at < now - result-retention
                    → cancella file result, status=DELETED, azzera campi pesanti
  fase 3 delete:    status=DELETED + deleted_at < now - tombstone-retention
                    → DELETE row
```

### 8.2 Worker validazione

Polling `@Scheduled` (default ogni 5s):
- Check circuit breaker DSS: se `OPEN` → skip pickup (HOLD strategy)
- `SELECT id FROM validation_job WHERE status='PENDING' AND pickup_attempts < :max ORDER BY created_at LIMIT :n FOR UPDATE SKIP LOCKED`
- Per ogni job: submit task a `validationExecutor` (pool default 4 thread)

Processo singolo job:
- Transazione 1: `PENDING → RUNNING`, `started_at=now`, `pickup_attempts++`
- Fuori transazione: `DssValidatorAdapter.validate(...)` con `@CircuitBreaker`
- Transazione 2: scrivi result JSON su FS, `status=COMPLETED|FAILED`, `completed_at=now`, `next_callback_at = now` (se callbackUrl != null)

Eccezione DSS recoverable (es. circuit breaker scattato durante esecuzione): rollback, job torna PENDING, `pickup_attempts++`. Cap default 10 → `FAILED` con `error="dss_unavailable_after_max_attempts"`.

Eccezione non-recoverable (es. documento corrotto): `FAILED` direttamente.

### 8.3 Worker callback dispatch

Polling `@Scheduled` (default ogni 10s) con `@SchedulerLock(name="callbackDispatch")`:
- `SELECT id FROM validation_job WHERE status IN ('COMPLETED','FAILED') AND callback_url IS NOT NULL AND next_callback_at <= now AND callback_attempts < :max ORDER BY next_callback_at LIMIT :n FOR UPDATE SKIP LOCKED`
- Per ogni: submit a `callbackExecutor`

Dispatch:
1. Decifra `callback_secret_cipher` (AES-GCM master key)
2. Costruisci body JSON
3. Calcola firma HMAC
4. POST con timeout 15s
5. Response:
   - `success-statuses` (2xx) → `status=DELIVERED`, `delivered_at=now`
   - `non-retryable-statuses` (4xx tranne 408/425/429) → `status=DELIVERY_FAILED`, `last_callback_error=...`
   - `retryable-statuses` o exception network → `callback_attempts++`, `next_callback_at = now + backoff[attempts-1]`
6. Se `attempts >= max-attempts` → `DELIVERY_FAILED`

### 8.4 HMAC signing dettagli

**Body**:
```json
{
  "jobId": "uuid",
  "status": "COMPLETED",
  "verifiedAt": "...",
  "profileUsed": "...",
  "signatureFormat": "PAdES-LTA",
  "indication": "TOTAL_PASSED",
  "subIndication": null,
  "reports": { ... requested only ... },
  "error": null
}
```

Per `FAILED`: `status="FAILED"`, `error={code, message}`, `reports` omesso.

**Header firmati**:
```
Content-Type: application/json
X-Timestamp: 1717760591           (epoch seconds)
X-Nonce: 6f3...c2                 (UUID v4)
X-Signature: sha256=<hex>         (o sha512=...)
X-Signature-Algorithm: HmacSHA256
X-Job-Id: <jobId>
X-Delivery-Id: <UUID v4>          (univoco per ogni tentativo)
X-Delivery-Attempt: 1
User-Agent: sign-verify/1.0
```

**Stringa canonica firmata**:
```
<X-Timestamp> + "\n" + <X-Nonce> + "\n" + <X-Delivery-Id> + "\n" + sha256(body bytes hex)
```
Firma = `HMAC(algo, secret, stringaCanonica)`.

Includere `sha256(body)` invece del body raw evita ambiguità su encoding/newline. Documentato nell'OpenAPI.

**Verifica lato ricevente** (raccomandata):
1. `|now - X-Timestamp| < 5 min`
2. Nonce non già visto (cache)
3. Ricalcolo HMAC + confronto constant-time
4. Idempotency su `X-Job-Id` (tentativi multipli stesso jobId hanno result identico)

### 8.5 Configurazione callback

```yaml
app:
  callback:
    max-attempts: 3
    backoff: [60s, 300s, 1800s]
    success-statuses: [200, 201, 202, 204]
    retryable-statuses: [408, 425, 429, 500, 502, 503, 504]
    non-retryable-statuses: [400, 401, 403, 404, 410, 422]
    timeout: 15s
    hmac-default-algorithm: HmacSHA256
    allowed-algorithms: [HmacSHA256, HmacSHA512]
    allow-http: false              # prod: false (solo https)
    block-private-networks: true   # prod: true (no RFC1918)
```

### 8.6 Secret callback at-rest

`callback_secret` necessita di restare disponibile in chiaro per firmare → **cifrato at-rest** (AES-GCM via `SecretCipherPort`):
- Master key da env var (`APP_SECRET_MASTER_KEY`, base64, 256-bit)
- IV random per ogni cifratura
- Stoccato come `iv || ciphertext || tag` base64

### 8.7 Retention + recupero esplicito

**Recupero via GET** `/api/v1/verifications/jobs/{jobId}` è canale parallelo al callback:
- Cliente può non passare `callbackUrl` → solo polling/recupero esplicito
- Cliente con callback può comunque GET in parallelo
- `DELIVERED` non significa "result indisponibile": result resta fino a tombstone

**Retention multi-fase**:

```yaml
app:
  async:
    job-ttl: 7d              # PENDING/RUNNING max age (poi FAILED)
    input-retention: 1h      # documento input dopo terminal state
    result-retention: 30d    # result JSON + ownership info pre tombstone
    tombstone-retention: 30d # row con minima info post tombstone
```

```
Cleanup scheduler (giornaliero, ShedLock):
  fase 1 — INPUT:
    job in terminal state AND completed_at < now - input-retention
    → delete document_path file, document_path=NULL
  
  fase 2 — RESULT + TOMBSTONE:
    job in terminal state AND completed_at < now - result-retention
    AND status != 'DELETED'
    → delete result_path file
    → status='DELETED', original_status=<vecchio>, deleted_at=now
    → null: callback_secret_cipher, result_path, callback_url, profile_overrides,
            error_message, last_callback_error
    → keep: id, original_status, completed_at, deleted_at, requested_by_principal_*
  
  fase 3 — ROW DELETE:
    status='DELETED' AND deleted_at < now - tombstone-retention
    → DELETE row
  
  fase 4 — EXPIRED:
    status IN ('PENDING','RUNNING') AND expires_at < now
    → status='FAILED', error_message='job_expired', schedula callback se presente
```

### 8.8 Request submit async — esempio completo

```http
POST /api/v1/verifications/async HTTP/1.1
X-API-Key: sv_xxxxxxxx_...
Content-Type: multipart/form-data; boundary=---X

-----X
Content-Disposition: form-data; name="file"; filename="contract.pdf"
Content-Type: application/pdf
<binary>
-----X
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{
  "profileId": "uuid",
  "profileOverrides": { "checkRevocation": false },
  "reports": ["simple", "etsi"],
  "callbackUrl": "https://client.example/cb",
  "callbackSecret": "supersecret",
  "callbackAlgorithm": "HmacSHA256"
}
-----X--
```

Response:
```http
202 Accepted
Location: /api/v1/verifications/jobs/<uuid>

{
  "jobId": "<uuid>",
  "status": "PENDING",
  "expiresAt": "..."
}
```

---

## 9. Protezione carico + coordinamento multi-istanza

### 9.1 Deploy

Multi-istanza (HA/scale-out). Coordinamento via DB + ShedLock.

### 9.2 Concurrency limit verifiche sync

- `Semaphore` per-istanza dimensione `app.verify.max-concurrent` (default `max(8, cores*2)`)
- `tryAcquire(timeout=2s)`; fallita → `503 excessive-load.concurrency` + `Retry-After: 2`
- Wrapper applicato in `VerificationService.verifySync()`
- Metriche Micrometer: `signverify.verify.concurrent.active` (gauge), `.rejected` (counter)

Per-istanza: limite globale = limit × #istanze (accettabile, scale-out trasparente).

### 9.3 Async job back-pressure

- `app.async.max-pending-per-principal` (default 50)
- `app.async.max-pending-global` (default 500)
- Check pre-insert in transazione su submit async
- Eccesso → `429 excessive-load.async-backpressure` + `Retry-After: 30`

### 9.4 Circuit breaker DSS

`Resilience4j @CircuitBreaker(name="dssValidator")` su `DssValidatorAdapter.validate(...)` e `DssExtractionAdapter.extract(...)`.

```yaml
resilience4j.circuitbreaker:
  instances:
    dssValidator:
      failureRateThreshold: 50
      slidingWindowSize: 20
      waitDurationInOpenState: 60s
      slowCallDurationThreshold: 30s
      slowCallRateThreshold: 80
      permittedNumberOfCallsInHalfOpenState: 3
```

- Modalità **sync**: CB aperto → `503 dss.unavailable`
- Modalità **async**: HOLD strategy (vedi §8.2)

### 9.5 Coordinamento async worker

- Postgres prod: `SELECT ... FOR UPDATE SKIP LOCKED` su `validation_job`
- H2 dev (single-instance assumption)
- Worker pool config: `app.async.workers` (default 4)

### 9.6 ShedLock scheduler

Tabella `shedlock` su stesso DB. Lock per ogni scheduled task:
- `tslRefresh` (cron giornaliero)
- `callbackDispatch` (ogni 10s)
- `jobCleanup` (giornaliero)

Solo un nodo esegue il task. `lockAtLeastFor` previene retry storm.

---

## 10. Cross-cutting

### 10.1 Error handling

Tutti gli errori `application/problem+json` (RFC 9457):
```json
{
  "type": "urn:signverify:error:resource.not-found",
  "title": "Profilo non trovato",
  "status": 404,
  "detail": "Nessun profilo con id 'abc'",
  "instance": "/api/v1/verifications",
  "traceId": "..."
}
```

`@RestControllerAdvice` globale mappa eccezioni di dominio a problem details. Nessuno stack trace leakato in produzione.

### 10.2 Observability

#### Logging
- Logback con encoder JSON (`logstash-logback-encoder`)
- MDC: `requestId`, `principalId`, `principalType`, `clientIp`
- INFO per ogni request (path/method/status/duration/principal)
- Niente PII o file content nei log
- DSS logger livello WARN (silenzia verbosity)

#### Actuator
- `/actuator/health/liveness`
- `/actuator/health/readiness` — include check TSL bootstrap, DB, ShedLock table
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

#### Metriche custom
- `signverify.verify.sync.count{result}` (ok/fail/rejected)
- `signverify.verify.sync.duration` (histogram)
- `signverify.verify.concurrent.active` (gauge)
- `signverify.async.jobs.pending|running|delivered|delivery_failed` (gauge)
- `signverify.callback.attempts.count{result}`
- `signverify.tsl.refresh.count{result}`, `.duration`, `.certificates`
- `signverify.dss.circuit_state` (gauge: 0=CLOSED, 1=HALF_OPEN, 2=OPEN)

### 10.3 Audit log

Append-only, popolato via aspect/interceptor. Azioni tracciate:
- `API_KEY_CREATE`, `API_KEY_DELETE`, `API_KEY_ENABLE`, `API_KEY_DISABLE`
- `PROFILE_CREATE`, `PROFILE_UPDATE`, `PROFILE_DELETE`, `PROFILE_SET_DEFAULT`
- `VERIFICATION_SYNC`, `VERIFICATION_ASYNC_SUBMIT`, `VERIFICATION_GET_RESULT`
- `EXTRACTION`
- `TSL_REFRESH_TRIGGERED`
- `AUTH_FAILURE`

`details` contiene contesto JSON (es. jobId, profileId, refreshId, durata).

---

## 11. Catalogo codici errore

| Code (`type` URN) | HTTP | Quando |
|---|---|---|
| `validation.invalid-input` | 400 | Bean Validation failure |
| `validation.invalid-profile-overrides` | 400 | JSON override non parsabile |
| `auth.missing-credentials` | 401 | nessun token/api key |
| `auth.invalid-token` | 401 | API key non valida/disabled/scaduta o JWT invalid |
| `authz.forbidden` | 403 | role insufficiente |
| `resource.not-found` | 404 | profilo/job/api-key id non esistente, o non-owner non-priv |
| `resource.gone` | 410 | job tombstone (post-retention result) |
| `resource.conflict` | 409 | ultima PRIVILEGED, profilo default, refresh in corso |
| `payload.too-large` | 413 | file > limit |
| `media.unsupported` | 415 | content-type errato |
| `signature.parse-error` | 422 | DSS non riesce a parsare documento |
| `excessive-load.concurrency` | 503 | semaforo verifica esaurito |
| `excessive-load.async-backpressure` | 429 | cap pending async raggiunto |
| `tsl.not-ready` | 503 | bootstrap BACKGROUND incompleto |
| `dss.unavailable` | 503 | circuit breaker open |
| `internal-error` | 500 | uncaught (sanitized) |

---

## 12. Testing

### 12.1 Unit
- JUnit 5 + AssertJ + Mockito
- Service tests con mock dei port. Coverage target ≥ 80% su `application` e `domain`
- HMAC signer/verifier deterministico con fixture vector
- Override-XML applier: snapshot test su 4-5 combinazioni

### 12.2 Integration (`*IT.java`, Failsafe)
- `@SpringBootTest` con Testcontainers Postgres reale
- WireMock per IdP OIDC + TSL stub
- Profilo Spring `test` con DSS offline + TSL cache pre-popolata da fixture
- Fixtures: documenti firmati reali in `src/test/resources/signatures/` (1-2 per formato)
- Test contract: `swagger-request-validator-mockmvc` verifica response shape vs `openapi.yaml`

### 12.3 ArchUnit
- Direzionalità delle dipendenze enforced
- Vincoli §3.4

### 12.4 End-to-end signature
- `VerificationFlowIT`:
  - PAdES valido → `TOTAL_PASSED`
  - PAdES manomesso → `TOTAL_FAILED`, `subIndication=HASH_FAILURE`
  - Async flow → callback su WireMock, validazione HMAC server-side

### 12.5 HMAC callback
- WireMock listener verifica `X-Signature` su body ricevuto
- Test 429 → retry, 200 → DELIVERED, 4xx non-retryable → DELIVERY_FAILED subito

### 12.6 TSL
- WireMock TSL stub server (LOTL + 1 TSL nazionale fake)
- Verifica mirror DB popolato + diff refresh successivi

---

## 13. Build + deploy

### 13.1 Maven plugin

- `openapi-generator-maven-plugin` — genera DTO + controller interface da `openapi.yaml` in `target/generated-sources`. Package `org.toresoft.signverify.api.dto` e `.spi`
- `spring-boot-maven-plugin` — repackage + build-info
- `maven-surefire-plugin` (unit)
- `maven-failsafe-plugin` (integration `*IT`)
- `jacoco-maven-plugin` — rule 80% line su `application` + `domain`
- `spotless-maven-plugin` — formatting + import order + license header
- `flyway-maven-plugin` — migration utility

### 13.2 Dipendenze principali

```
spring-boot-starter-web
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-oauth2-resource-server
spring-boot-starter-actuator
spring-boot-starter-validation
flyway-core
postgresql, h2 (test)
eu.europa.ec.joinup.sd-dss:dss-bom (import)
  → dss-document, dss-validation, dss-tsl-validation,
    dss-pades, dss-cades, dss-xades, dss-jades,
    dss-asic-cades, dss-asic-xades
io.github.resilience4j:resilience4j-spring-boot3
io.github.resilience4j:resilience4j-circuitbreaker
net.javacrumbs.shedlock:shedlock-spring + shedlock-provider-jdbc-template
com.github.ben-manes.caffeine:caffeine
io.micrometer:micrometer-registry-prometheus
net.logstash.logback:logstash-logback-encoder
org.bouncycastle:bcprov-jdk18on
springdoc-openapi-starter-webmvc-ui (opzionale per swagger UI dev)
junit, assertj, mockito, testcontainers, wiremock, archunit, swagger-request-validator-mockmvc (test)
```

### 13.3 Dockerfile multi-stage

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -e dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN useradd -u 10001 -m -s /sbin/nologin app
WORKDIR /app
COPY --from=build /app/target/sign-verify-2-*.jar app.jar
RUN mkdir -p /var/lib/sign-verify/{dss-cache,jobs} \
 && chown -R app:app /var/lib/sign-verify
USER app
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
HEALTHCHECK CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
```

### 13.4 GitLab CI pipeline

Stages: `validate` → `test` → `build` → `package` → `security`

Job principali:
- `validate:openapi` — `openapi-generator-cli validate -i openapi.yaml`
- `validate:format` — `mvn spotless:check`
- `test:unit` — `mvn test`
- `test:integration` — `mvn verify` (docker:dind per Testcontainers)
- `test:coverage` — `mvn jacoco:report`
- `build:jar` — `mvn -DskipTests package`
- `package:docker` — `docker build + push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA` (+ tag `latest` su main)
- `security:dependency-scan` — `org.owasp:dependency-check-maven` (allow_failure)

### 13.5 Configurazione esternalizzata

- `application.yaml` defaults
- `application-dev.yaml`, `application-prod.yaml`, `application-test.yaml`
- Override via env var Spring (12-factor)
- Secret (DB password, OJ keystore pwd, OIDC client secret, callback master key) **solo via env var**

### 13.6 Variabili ambiente principali

| Variabile | Scopo |
|---|---|
| `SPRING_PROFILES_ACTIVE` | profilo |
| `SPRING_DATASOURCE_URL` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | credenziali DB |
| `APP_OJ_KEYSTORE_PASSWORD` | password keystore OJ |
| `APP_SECURITY_OAUTH_ISSUER_URI` | IdP OIDC issuer |
| `APP_SECURITY_OAUTH_ROLE_CLAIM` | claim per role mapping |
| `APP_SECURITY_OAUTH_PRIVILEGED_VALUES` | valori claim che mappano a PRIVILEGED |
| `APP_SECURITY_BOOTSTRAP_KEY_FILE` | path file bootstrap key |
| `APP_SECRET_MASTER_KEY` | master key AES-GCM per cifratura secret callback |
| `APP_DSS_CACHE_DIR` | dir cache TSL |
| `APP_STORAGE_JOBS_DIR` | dir file job |

---

## 14. Rischi e considerazioni

| Rischio | Mitigazione |
|---|---|
| OJ keystore scaduto/obsoleto | Procedura operativa di aggiornamento bundled, alert se DSS LOTL alert scatta |
| TSL source down al refresh | Diff parziale (`PARTIAL` status), allarmi Prometheus |
| Documento firmato grande (es. 50MB) → memoria | Max-size limit, validation con stream se DSS lo supporta; OOM-Killer protezione via heap limit Docker |
| Callback endpoint cliente abusato per scan interni | `block-private-networks` prod, `allow-http=false` prod |
| Master key compromessa | Rotazione: re-cifratura batch dei secret callback. Procedura documentata |
| DSS bug critico in produzione | Pin versione, regressione test suite, rollback Docker image |
| Filesystem riempito da job orfani | Cleanup scheduler + alarm su disk usage |

---

## 15. Open questions / future enhancements (non v1)

- Rate limit token-bucket per principal (Bucket4j + Redis store)
- Multi-tenancy con tenant isolation
- Estrazione delegata su S3 / object storage (port già astratto)
- Native image GraalVM (richiede config reflection DSS)
- Linking opzionale OAuth user ↔ API key per ownership cross-credential

---

## 16. Note di esecuzione

Successivo: writing-plans skill produce piano implementativo iterativo con task numerati, ordinati per dipendenze, ognuno verificabile.
