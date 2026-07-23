# 1. Compilazione e configurazione

← [0. Glossario](00-glossario.md) · [Indice](README.md) · → [1b. Docker e configurazione](02-docker.md)

## 1.1 Prerequisiti

| Requisito | Versione | Note |
|-----------|----------|------|
| JDK | **21** | `maven.compiler.source`/`target` = 21 nel `pom.xml` |
| Maven | 3.9+ | nessun wrapper nel repository; usare un `mvn` di sistema |
| PostgreSQL | 16 (in esercizio) | nei test e in sviluppo si usa H2 in memoria |
| Docker | facoltativo | per i test di integrazione (Testcontainers) e per l'installazione |

Gli strumenti locali (`mvn` e `java`) sono forniti da **SDKMAN**, sotto
`~/.sdkman`. Se `mvn` non è nel `PATH`:

```bash
source ~/.sdkman/bin/sdkman-init.sh
```

## 1.2 Compilazione e test

```bash
# Compilazione
mvn compile

# Test unitari e di integrazione (i test di integrazione usano Postgres via Testcontainers)
mvn test

# Una singola classe o un singolo metodo di test
mvn test -Dtest=DssValidatorAdapterTest
mvn test -Dtest=ApiKeyServiceTest#create

# Verifica completa (test di integrazione con Failsafe, controllo Spotless, rapporto JaCoCo)
mvn verify

# Formattazione del codice (Google Java Format)
mvn spotless:apply

# Creazione del jar
mvn package
```

> **Spotless** (Google Java Format) viene applicato durante `verify`. Eseguire
> sempre `mvn spotless:apply` prima di ogni commit.

### Sequenza di compilazione

```mermaid
flowchart LR
    A[openapi.yaml] -->|OpenAPI Generator| B["Interfacce api.spi\n+ DTO api.dto"]
    B --> C[mvn compile]
    C --> D[Test unitari]
    D --> E["Test di integrazione Failsafe\nTestcontainers Postgres"]
    E --> F[Controllo Spotless]
    F --> G[Rapporto JaCoCo]
    G --> H[sign-verify-2.jar]

    classDef source fill:#dbeeff,stroke:#2f6fbb,color:#0b2e4f
    classDef step fill:#eef1f5,stroke:#5b6b7c,color:#1f2733
    classDef artifact fill:#e1f5e9,stroke:#2f8a4e,color:#0d3a1d
    class A source
    class B,C,D,E,F,G step
    class H artifact
```

L'API nasce dal contratto e non dal codice: il contratto vive in
`src/main/resources/openapi/openapi.yaml` e il plugin **OpenAPI Generator**
produce le interfacce (`api.spi`) e i DTO (`api.dto`). A fare da guardiano del
contratto è il test `OpenApiContractIT`.

## 1.3 Profili Spring

Il servizio carica `application.yaml`, valido per tutti, più un file aggiuntivo
per ogni profilo:

| Profilo | File | Scopo |
|---------|------|-------|
| _(nessuno)_ | `application.yaml` | Impostazioni di produzione: H2 in memoria se `SPRING_DATASOURCE_URL` non è impostato, OAuth **abilitato** |
| `dev` | `application-dev.yaml` | Sviluppo locale: OAuth **disabilitato**, chiave master di prova, aggiornamento delle liste di fiducia **saltato** |
| `docker` | `application-docker.yaml` | Esecuzione in container: OAuth disabilitato, aggiornamento delle liste saltato, sorgente dati da `SPRING_DATASOURCE_*` |

Avvio locale con profilo `dev`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## 1.4 Parametri di configurazione

Tutti i parametri si possono sovrascrivere con variabili d'ambiente, usando in
YAML la notazione `${VAR:valore-predefinito}`. Di seguito le sezioni principali
sotto la chiave `app:`.

### Sicurezza (`app.security`)

| Chiave | Variabile d'ambiente | Predefinito | Descrizione |
|--------|----------------------|-------------|-------------|
| `oauth.enabled` | `APP_SECURITY_OAUTH_ENABLED` | `true` | Abilita il resource server JWT |
| `oauth.role-claim` | `APP_SECURITY_OAUTH_ROLE_CLAIM` | `roles` | Claim JWT da cui leggere i ruoli |
| `oauth.privileged-values` | `APP_SECURITY_OAUTH_PRIVILEGED_VALUES` | `admin,privileged` | Valori del claim che concedono `PRIVILEGED` |
| `bootstrap-key-file` | `APP_SECURITY_BOOTSTRAP_KEY_FILE` | `/var/lib/sign-verify/bootstrap-api-key.txt` | File in cui viene scritta la chiave iniziale (bootstrap) |
| `master-key` | `APP_SECRET_MASTER_KEY` | _(vuoto)_ | Chiave base64 a 256 bit per cifrare i segreti a riposo |

Issuer JWT (resource server):
`spring.security.oauth2.resourceserver.jwt.issuer-uri` ← `APP_SECURITY_OAUTH_ISSUER_URI`.

### Memorizzazione dei documenti e DSS

| Chiave | Variabile d'ambiente | Predefinito | Descrizione |
|--------|----------------------|-------------|-------------|
| `storage.type` | `APP_STORAGE_TYPE` | `filesystem` | `filesystem` o `s3`. Con `filesystem` i documenti restano nella cartella `jobs-dir`; con `s3` vengono salvati in un archivio a oggetti compatibile S3, che è quello che consente di replicare il servizio (vedi [08-scalabilita-orizzontale.md](08-scalabilita-orizzontale.md)) |
| `storage.jobs-dir` | `APP_STORAGE_JOBS_DIR` | `/var/lib/sign-verify/jobs` | Solo con `filesystem` |
| `storage.s3.endpoint` | `APP_STORAGE_S3_ENDPOINT` | _(vuoto)_ | Vuoto = endpoint AWS nativo; va impostato per MinIO, Ceph e simili |
| `storage.s3.region` | `APP_STORAGE_S3_REGION` | `eu-south-1` | |
| `storage.s3.bucket` | `APP_STORAGE_S3_BUCKET` | `sign-verify-jobs` | Il bucket deve esistere: all'avvio l'adattatore esegue una `headBucket` e il servizio si arresta se il bucket manca o non è raggiungibile |
| `storage.s3.path-style-access` | `APP_STORAGE_S3_PATH_STYLE` | `false` | `false` = indirizzamento virtual-hosted (AWS nativo); `true` richiesto da MinIO e Ceph |
| `dss.cache-dir` | `APP_DSS_CACHE_DIR` | `/var/lib/sign-verify/dss-cache` | |
| `dss.online-mode` | — | `true` | |
| `upload.max-size` | — | `50MB` | Caricamento multipart: 50MB per file, 60MB per richiesta |

Le chiavi degli oggetti sono relative (`{jobId}/input-{safeName}`,
`{jobId}/result.json`) e vengono salvate sul database al posto dei percorsi
assoluti del filesystem.

Le credenziali S3 **non** sono una proprietà applicativa: il client di AWS SDK v2
le cerca sempre nella catena predefinita (variabili `AWS_ACCESS_KEY_ID` e
`AWS_SECRET_ACCESS_KEY`, ruoli IAM o IRSA, profili locali), così nessun segreto
finisce nel file di configurazione.

> **Cambio di modalità**: `filesystem` salva percorsi assoluti, `s3` salva chiavi
> relative, e le due cose non si mescolano. Per cambiare modalità occorre prima
> svuotare le code (i job hanno tempi di conservazione brevi e vengono puliti
> automaticamente); non è prevista alcuna migrazione dei dati. La procedura
> completa è nel capitolo [08](08-scalabilita-orizzontale.md).

### Trusted Lists (`app.tsl`)

| Chiave | Predefinito | Descrizione |
|--------|-------------|-------------|
| `sources[0].url` | `https://ec.europa.eu/tools/lotl/eu-lotl.xml` | EU LOTL |
| `sources[0].pivot-support` | `true` | Supporto ai pivot LOTL |
| `sources[0].oj-keystore-path` | `classpath:keystore/oj-keystore.p12` | Keystore con i certificati OJ |
| `refresh.cron` | `0 0 2 * * *` | Aggiornamento giornaliero alle 02:00 |
| `refresh.timezone` | `Europe/Rome` | |
| `refresh.startup-mode` | `BACKGROUND` | `BACKGROUND` / `SKIP` |

La password del keystore OJ va in `APP_OJ_KEYSTORE_PASSWORD`.

### Verifica sincrona / asincrona

| Chiave | Predefinito | Descrizione |
|--------|-------------|-------------|
| `verify.max-concurrent` | `8` | Verifiche sincrone simultanee (semaforo) |
| `async.workers` | `4` | Numero di worker di validazione |
| `async.worker.poll-interval` | `5s` | Intervallo tra un'interrogazione della coda e la successiva |
| `async.max-pending-per-principal` | `50` | Limite di job in attesa per singolo chiamante |
| `async.max-pending-global` | `500` | Limite complessivo di job in attesa |
| `async.job-ttl` | `7d` | Durata massima di vita del job |
| `async.input-retention` | `1h` | Conservazione del documento in ingresso |
| `async.result-retention` | `30d` | Conservazione del risultato |
| `async.cleanup.cron` | `0 30 3 * * *` | Pulizia giornaliera alle 03:30 |

### Callback / webhook (`app.callback`)

| Chiave | Predefinito | Descrizione |
|--------|-------------|-------------|
| `max-attempts` | `3` | Tentativi di consegna |
| `backoff` | `60s,300s,1800s` | Attesa crescente tra un tentativo e il successivo |
| `success-statuses` | `200,201,202,204` | Stati HTTP che valgono come consegna riuscita |
| `retryable-statuses` | `408,425,429,500,502,503,504` | Stati HTTP dopo i quali si riprova |
| `timeout` | `15s` | Tempo massimo della richiesta |
| `allowed-algorithms` | `HmacSHA256,HmacSHA512` | Algoritmi HMAC ammessi |
| `allow-http` | `false` | Se `false`, solo HTTPS |
| `block-private-networks` | `true` | Guardia anti-SSRF (vedi [05](05-verifica-firme.md)) |

### Database

```yaml
spring.datasource.url: ${SPRING_DATASOURCE_URL:jdbc:h2:mem:dev;...;MODE=PostgreSQL}
spring.datasource.username: ${SPRING_DATASOURCE_USERNAME:sa}
spring.datasource.password: ${SPRING_DATASOURCE_PASSWORD:}
```

Lo schema è gestito da **Flyway** (`db/migration/V*__*.sql`). Hibernate è in
`ddl-auto: validate`: **non** altera lo schema. Per modifiche allo schema,
aggiungere una nuova migrazione `V__*.sql`.

## 1.5 Variabili minime per la produzione

```bash
SPRING_PROFILES_ACTIVE=          # vuoto = profilo di produzione; NON 'dev' né 'docker'
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/signverify
SPRING_DATASOURCE_USERNAME=signverify
SPRING_DATASOURCE_PASSWORD=********
APP_SECRET_MASTER_KEY=<base64 di 32 byte casuali>
APP_OJ_KEYSTORE_PASSWORD=<password keystore OJ>
# se si usa OAuth:
APP_SECURITY_OAUTH_ISSUER_URI=https://idp.example.org/realms/sign
```

Generazione di una chiave master valida:

```bash
openssl rand -base64 32
```
