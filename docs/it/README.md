# Documentazione d'uso — sign-verify

Guida operativa al servizio **sign-verify**: verifica di firme digitali eIDAS
(PAdES / CAdES / XAdES / JAdES / ASiC) basata sulla libreria **DSS 6.4** e sulle
**EU Trusted Lists** (LOTL/TSL).

> 🇬🇧 English version: [`docs/en/README.md`](../en/README.md)
>
> 🐳 Immagine Docker sul registry: **[`toresoft/sign-verify`](https://hub.docker.com/r/toresoft/sign-verify)** (Docker Hub)

## Indice

| # | Argomento | File |
|---|-----------|------|
| 1 | Compilazione e configurazione | [01-build-configurazione.md](01-build-configurazione.md) |
| 1b | Docker e configurazione | [02-docker.md](02-docker.md) |
| 1c | **Guida operativa Docker (passo-passo)** | [02b-guida-operativa-docker.md](02b-guida-operativa-docker.md) |
| 2 | Autenticazione (panoramica, API key, OAuth) | [03-autenticazione.md](03-autenticazione.md) |
| 3 | API Trusted Certificates (TSL) | [04-trusted-certificates.md](04-trusted-certificates.md) |
| 4 | Verifica firme: introduzione, profili, sync, async | [05-verifica-firme.md](05-verifica-firme.md) |
| 5 | Estrazione file originali | [06-estrazione-file.md](06-estrazione-file.md) |
| 6 | Log e audit | [07-log-audit.md](07-log-audit.md) |

## Mappa dei componenti

```mermaid
flowchart LR
    Client([Client]) -->|X-API-Key / Bearer JWT| API[REST API\n/api/v1/**]
    API --> Sec[Security Filter\nAPI key / OAuth]
    Sec --> App[Application Services]
    App --> DSS[Adapter DSS 6.4]
    App --> DB[(PostgreSQL\nFlyway schema)]
    App --> Store[(Filesystem\njob storage)]
    DSS --> TSL[(EU LOTL/TSL\nTrusted Lists)]
    App -.->|webhook HMAC| CB[[Callback esterni]]
```

## Architettura in breve

Il servizio è un'applicazione **Spring Boot 3.5 / Java 21** con architettura
**esagonale** (ports & adapters), verificata da ArchUnit. Package radice
`org.toresoft.signverify`:

- `api/` — controller REST sottili + `GlobalExceptionHandler` (RFC 9457 `problem+json`)
- `application/` — servizi che orchestrano i casi d'uso
- `domain/` — entità, enum, porte (interfacce), `AppException`
- `adapter/` — implementazioni delle porte (DSS, crypto, callback, storage)
- `persistence/` — repository Spring Data JPA
- `security/` — filtro API key, converter OAuth, generatore chiave di bootstrap
- `config/` — configurazione Security, DSS, metriche, scheduler, TSL

Lo schema DB è di proprietà di **Flyway** (`src/main/resources/db/migration`);
Hibernate gira con `ddl-auto: validate`.
