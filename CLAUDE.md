# CLAUDE.md

Guida per Claude Code in questo repository.

## Lingua
- Rispondere sempre in italiano, salvo diversa indicazione esplicita.
- Commit git e commenti nel codice: sempre in inglese.
- Piani e specifiche: in italiano.

## Commit git
- Non aggiungere mai il footer `Co-Authored-By: Claude`.

## Cosa fa il progetto

Servizio Spring Boot che **verifica firme digitali** eIDAS (PAdES / CAdES / XAdES / JAdES / ASiC)
con la libreria **DSS** e le EU Trusted Lists (LOTL/TSL).
Coordinate artefatto: `org.toresoft:sign-verify-2:1.0.0-SNAPSHOT`.

## Dove trovare le informazioni sul progetto

La conoscenza dettagliata del progetto (architettura, domini, concetti DSS/eIDAS, entità,
decisioni di design, ricerche) vive in un **wiki locale** sotto `.llm-wiki/`.

**Prima di rispondere a domande su architettura, domini o scelte tecniche, consultare il wiki:**

- Interrogare in linguaggio naturale: `/wiki:query <domanda>` (usare `--deep` per analisi approfondite).
- Indice navigabile: `.llm-wiki/meta/index.md` — 63 pagine tra `entities/`, `concepts/`,
  `syntheses/`, `analyses/`, `sources/`.
- Esempi di voci utili: `concepts/hexagonal-architecture`, `concepts/design-first-openapi`,
  `concepts/api-key-authentication`, `concepts/trusted-lists`, `entities/sign-verify-2`.

I piani e le specifiche di implementazione (in italiano) sono in `docs/superpowers/`.

## Build & test

Toolchain via SDKMAN; se `mvn` non è nel `PATH`: `source ~/.sdkman/bin/sdkman-init.sh`.

```bash
mvn compile                                   # compila
mvn test                                       # unit + IT (Testcontainers Postgres)
mvn test -Dtest=DssValidatorAdapterTest        # singola classe
mvn test -Dtest=ApiKeyServiceTest#create       # singolo metodo
mvn verify                                      # Failsafe IT + Spotless check + JaCoCo
mvn spotless:apply                              # format (Google Java Format, obbligatorio prima del commit)
mvn package                                     # package
```

Run locale profilo `dev` (OAuth off, master-key di test, refresh TSL saltato):
`mvn spring-boot:run -Dspring-boot.run.profiles=dev`.

## Convenzioni chiave

- Java 21, Spring Boot 3.4.1, Maven (no wrapper).
- Architettura esagonale (ports & adapters), garantita da ArchUnit (`ArchitectureTest`).
- API **design-first**: editare `src/main/resources/openapi/openapi.yaml`; il generatore produce
  `api.spi` / `api.dto`. `OpenApiContractIT` protegge il contratto.
- Schema DB di proprietà di **Flyway** (`src/main/resources/db/migration`); Hibernate è in
  `ddl-auto: validate`. Aggiungere una migration `V__*.sql`, non far alterare lo schema a Hibernate.
- Type hints stretti; constructor injection; i controller non espongono entità — mappare a DTO.
- Errori custom estendono/producono `AppException` → emessi come `application/problem+json` (RFC 9457).
- Eseguire `mvn spotless:apply` prima di ogni commit.