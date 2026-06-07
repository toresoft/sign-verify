# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Lingua
- Rispondere sempre in italiano, salvo diversa indicazione esplicita per il progetto.
- Commit git e commenti nel codice sempre in inglese.
- i piani e le specifiche devono essere in italiano

## Commit git
- Non aggiungere mai il footer Co-Authored-By: Claude nei messaggi di commit.

## Repository Overview

Spring Boot service that **verifies digital signatures** under the EU eIDAS framework
(PAdES / CAdES / XAdES / JAdES / ASiC) using the **DSS** library and EU Trusted Lists (LOTL/TSL).
Artifact coordinates: `org.toresoft:sign-verify-2:1.0.0-SNAPSHOT`.

Design and implementation plan live under `docs/superpowers/` (in Italian):
- `docs/superpowers/specs/2026-06-07-sign-verify-design.md`
- `docs/superpowers/plans/2026-06-07-sign-verify-implementation.md`

## Build Configuration

- **Build tool:** Maven (no wrapper committed — use a system `mvn`)
- **Java:** 21 (`maven.compiler.source`/`target` in `pom.xml`)
- **Parent:** `spring-boot-starter-parent` 3.4.1
- **Key deps:** DSS 6.4 (`dss-bom`), Spring Web/Data-JPA/Security/OAuth2-resource-server/Actuator,
  Flyway, PostgreSQL (runtime) + H2 (runtime/test), Resilience4j, ShedLock, Caffeine,
  Micrometer/Prometheus, springdoc-openapi.
- **Build plugins:** OpenAPI Generator (design-first: interfaces from `openapi.yaml`),
  Spotless (Google Java Format), JaCoCo, Failsafe.

The local toolchain is provided via SDKMAN (`mvn`/`java` live under `~/.sdkman`). If `mvn` is not on
`PATH`, run `source ~/.sdkman/bin/sdkman-init.sh` first.

## Common Commands

```bash
# Compile
mvn compile

# Unit + integration tests (Testcontainers Postgres for IT)
mvn test

# Single test class / method
mvn test -Dtest=DssValidatorAdapterTest
mvn test -Dtest=ApiKeyServiceTest#create

# Full verify (Failsafe IT + Spotless check + JaCoCo report)
mvn verify

# Format code
mvn spotless:apply

# Package
mvn package
```

Run locally with the `dev` profile (OAuth off, test master-key, TSL refresh skipped):
`mvn spring-boot:run -Dspring-boot.run.profiles=dev`.

## Architecture

Hexagonal (ports & adapters), enforced by ArchUnit (`ArchitectureTest`). Root package
`org.toresoft.signverify`:

- `api/` — REST controllers (thin) + `GlobalExceptionHandler` (RFC 9457 `problem+json`).
  API contract is **design-first**: edit `src/main/resources/openapi/openapi.yaml`; the generator
  produces interfaces/DTOs under `api.spi` / `api.dto`. `OpenApiContractIT` guards the contract.
- `application/` — services orchestrating use cases (verification, profiles, API keys, async jobs,
  callbacks, audit, TSL, cleanup/refresh schedulers).
- `domain/model`, `domain/port`, `domain/exception` — entities/enums, ports (interfaces), `AppException`.
- `adapter/` — port implementations: `dss/` (validation + extraction + TSL mirror),
  `crypto/` (AES-256-GCM cipher, bcrypt hasher), `callback/` (HMAC-signed webhook dispatcher),
  `storage/` (filesystem job storage).
- `persistence/` — Spring Data JPA repositories.
- `security/` — API-key auth filter, OAuth principal converter, bootstrap key generator.
- `config/` — Security, DSS, metrics, scheduler, TSL properties.

DB schema is owned by **Flyway** (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate`. Add a new `V__*.sql` migration rather than relying on Hibernate to alter schema.

## Authentication & Authorization

- Two mechanisms: **API key** (`X-API-Key: sv_<prefix>_<body>`, bcrypt-hashed, prefix indexed+unique)
  and optional **OAuth2 JWT** (`app.security.oauth.enabled`). Stateless, CSRF disabled (API only).
- Roles: `PRIVILEGED` / standard; `@EnableMethodSecurity` guards privileged endpoints.
- A `PRIVILEGED` bootstrap key is generated on first startup if none exists and written to
  `app.security.bootstrap-key-file` (0600). The "last enabled privileged key" cannot be removed.
- Secrets (e.g. callback HMAC secrets) are encrypted at rest with `app.security.master-key`
  (base64, 256-bit).

## Conventions

- Strict type hints; constructor injection; controllers never leak entities — map to DTOs.
- Custom errors extend/produce `AppException` → emitted as `application/problem+json`.
- Commit messages and code comments in English; specs/plans in Italian.
- Run `mvn spotless:apply` before committing (Google Java Format is enforced in `verify`).
