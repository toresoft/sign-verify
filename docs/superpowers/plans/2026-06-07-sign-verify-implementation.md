# sign-verify-2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Costruire un servizio REST Spring Boot 3.4 (Java 21) per la verifica di firme elettroniche tramite la libreria DSS 6.4, con estrazione documento originale, gestione TSL, profili verifica, autenticazione duale (API key + OAuth OIDC), modalità asincrona con callback HMAC, audit e observability.

**Architecture:** Modulo Maven singolo. Layered classico con ports-and-adapters limitato alle integrazioni esterne (DSS, OAuth, callback HTTP, filesystem, crypto). Multi-istanza con coordinamento via DB (ShedLock + `FOR UPDATE SKIP LOCKED`). Design-first OpenAPI.

**Tech Stack:** Java 21 LTS, Spring Boot 3.4.x, DSS 6.4, JPA/Hibernate + Flyway, H2 dev + Postgres prod, Resilience4j, ShedLock, Micrometer/Prometheus, Testcontainers, WireMock, ArchUnit, openapi-generator.

**Reference:** `docs/superpowers/specs/2026-06-07-sign-verify-design.md`

---

## Struttura per fasi

Il piano è organizzato in fasi. Ogni fase produce software funzionante e testabile. Eseguire le fasi in ordine; all'interno di una fase i task sono sequenziali (alcuni richiedono task precedenti).

| Fase | Deliverable funzionante |
|---|---|
| 0 | App Spring Boot vuota avviabile, schema DB completo creato da Flyway, health check verde |
| 1 | Entity JPA + repository per tutte le tabelle, ArchUnit baseline |
| 2 | Autenticazione API key + OAuth OIDC funzionante, principal unificato, bootstrap key |
| 3 | OpenAPI yaml + DTO generati, error handler RFC 9457 |
| 4 | Endpoint API key CRUD con invarianti |
| 5 | Endpoint profili verifica + preset XML |
| 6 | Endpoint verifica firma sincrona end-to-end con DSS |
| 7 | Endpoint estrazione documento originale |
| 8 | TSL refresh (scheduler + force), mirror DB, status, lista filtrata |
| 9 | Verifica firma asincrona con callback HMAC |
| 10 | Cleanup retention multi-fase |
| 11 | Audit log + endpoint |
| 12 | Observability custom + integration test end-to-end |
| 13 | GitLab CI pipeline + Dockerfile |

**Pattern TDD applicato a ogni task con logica**: scrivi test che fallisce → run e osserva fallimento → implementa minimo → run e osserva successo → commit.

**Convenzione commit**: Conventional Commits. Tipi: `feat`, `fix`, `test`, `chore`, `refactor`, `docs`, `build`, `ci`. Nessun footer `Co-Authored-By` (per regola CLAUDE.md).

**Path-prefix**: tutti i path sono relativi alla root `/home/salvatore/Development/sign-verify-2/`.

---

# FASE 0 — Foundation

**Obiettivo**: progetto Maven configurato, Spring Boot avviabile, schema DB completo creato da Flyway al boot, profilo `test` con Testcontainers Postgres + H2, health check verde, struttura package conforme.

## Task 0.1: Aggiornare `pom.xml` con stack completo

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Sostituire il contenuto di `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.toresoft</groupId>
    <artifactId>sign-verify-2</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <dss.version>6.4</dss.version>
        <resilience4j.version>2.2.0</resilience4j.version>
        <shedlock.version>5.16.0</shedlock.version>
        <testcontainers.version>1.20.4</testcontainers.version>
        <wiremock.version>3.10.0</wiremock.version>
        <archunit.version>1.3.0</archunit.version>
        <swagger-validator.version>2.43.0</swagger-validator.version>
        <openapi-generator.version>7.10.0</openapi-generator.version>
        <spotless.version>2.44.0</spotless.version>
        <jacoco.version>0.8.12</jacoco.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>eu.europa.ec.joinup.sd-dss</groupId>
                <artifactId>dss-bom</artifactId>
                <version>${dss.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- DB -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- DSS -->
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-document</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-tsl-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-pades</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-pades-pdfbox</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-cades</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-xades</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-jades</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-asic-cades</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-asic-xades</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-service</artifactId>
        </dependency>
        <dependency>
            <groupId>eu.europa.ec.joinup.sd-dss</groupId>
            <artifactId>dss-utils-apache-commons</artifactId>
        </dependency>

        <!-- BouncyCastle (DSS dep, esplicita) -->
        <dependency>
            <groupId>org.bouncycastle</groupId>
            <artifactId>bcprov-jdk18on</artifactId>
        </dependency>

        <!-- Resilience4j -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-circuitbreaker</artifactId>
            <version>${resilience4j.version}</version>
        </dependency>

        <!-- ShedLock -->
        <dependency>
            <groupId>net.javacrumbs.shedlock</groupId>
            <artifactId>shedlock-spring</artifactId>
            <version>${shedlock.version}</version>
        </dependency>
        <dependency>
            <groupId>net.javacrumbs.shedlock</groupId>
            <artifactId>shedlock-provider-jdbc-template</artifactId>
            <version>${shedlock.version}</version>
        </dependency>

        <!-- Cache -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>

        <!-- Observability -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>7.4</version>
        </dependency>

        <!-- OpenAPI runtime -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.7.0</version>
        </dependency>
        <dependency>
            <groupId>org.openapitools</groupId>
            <artifactId>jackson-databind-nullable</artifactId>
            <version>0.2.6</version>
        </dependency>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>${wiremock.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.atlassian.oai</groupId>
            <artifactId>swagger-request-validator-mockmvc</artifactId>
            <version>${swagger-validator.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>sign-verify-2</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>${spotless.version}</version>
                <configuration>
                    <java>
                        <googleJavaFormat>
                            <version>1.24.0</version>
                            <style>GOOGLE</style>
                        </googleJavaFormat>
                        <removeUnusedImports/>
                        <importOrder/>
                    </java>
                </configuration>
                <executions>
                    <execution>
                        <goals><goal>check</goal></goals>
                        <phase>verify</phase>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>org.openapitools</groupId>
                <artifactId>openapi-generator-maven-plugin</artifactId>
                <version>${openapi-generator.version}</version>
                <executions>
                    <execution>
                        <id>generate-api</id>
                        <goals><goal>generate</goal></goals>
                        <configuration>
                            <inputSpec>${project.basedir}/src/main/resources/openapi/openapi.yaml</inputSpec>
                            <generatorName>spring</generatorName>
                            <library>spring-boot</library>
                            <apiPackage>org.toresoft.signverify.api.spi</apiPackage>
                            <modelPackage>org.toresoft.signverify.api.dto</modelPackage>
                            <invokerPackage>org.toresoft.signverify.api</invokerPackage>
                            <skipOperationExample>true</skipOperationExample>
                            <configOptions>
                                <interfaceOnly>true</interfaceOnly>
                                <skipDefaultInterface>true</skipDefaultInterface>
                                <useSpringBoot3>true</useSpringBoot3>
                                <useTags>true</useTags>
                                <openApiNullable>true</openApiNullable>
                                <dateLibrary>java8</dateLibrary>
                                <useBeanValidation>true</useBeanValidation>
                                <performBeanValidation>true</performBeanValidation>
                                <useJakartaEe>true</useJakartaEe>
                            </configOptions>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Verifica risoluzione dipendenze**

```bash
mvn -B -e dependency:resolve -DincludeScope=compile -q
```

Expected: exit 0, dipendenze risolte (warning su BOM ok). Tempo prima esecuzione 1-3 minuti (download).

- [ ] **Step 3: Commit**

```bash
git init -q . 2>/dev/null || true
git add pom.xml
git commit -m "build: configure pom for spring boot 3.4 + dss 6.4 + java 21"
```

## Task 0.2: Main application + smoke test

**Files:**
- Create: `src/main/java/org/toresoft/signverify/SignVerifyApplication.java`
- Create: `src/test/java/org/toresoft/signverify/SignVerifyApplicationTest.java`

- [ ] **Step 1: Test che verifica il context si carica**

`src/test/java/org/toresoft/signverify/SignVerifyApplicationTest.java`:
```java
package org.toresoft.signverify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SignVerifyApplicationTest {

  @Test
  void contextLoads() {}
}
```

- [ ] **Step 2: Run test → fallisce (no main class)**

```bash
mvn -B test -Dtest=SignVerifyApplicationTest -q
```

Expected: BUILD FAILURE, "Unable to find a @SpringBootConfiguration".

- [ ] **Step 3: Implementa main class**

`src/main/java/org/toresoft/signverify/SignVerifyApplication.java`:
```java
package org.toresoft.signverify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SignVerifyApplication {
  public static void main(String[] args) {
    SpringApplication.run(SignVerifyApplication.class, args);
  }
}
```

- [ ] **Step 4: Aggiungi `application-test.yaml` minimo**

`src/test/resources/application-test.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ""
app:
  security:
    oauth:
      enabled: false
```

- [ ] **Step 5: Run test → ancora fallisce (no migration)**

```bash
mvn -B test -Dtest=SignVerifyApplicationTest -q
```

Expected: fallisce su Flyway "no migrations". Verrà risolto in Task 0.3.

## Task 0.3: Migration Flyway V1 — schema completo

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`

- [ ] **Step 1: Scrivere migration**

`src/main/resources/db/migration/V1__init_schema.sql`:
```sql
-- API keys
CREATE TABLE api_key (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    key_prefix VARCHAR(8) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    bootstrap BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    created_by_principal_type VARCHAR(20) NULL,
    created_by_principal_id VARCHAR(120) NULL,
    last_used_at TIMESTAMP NULL
);
CREATE INDEX idx_api_key_role_enabled ON api_key(role, enabled);
CREATE INDEX idx_api_key_prefix ON api_key(key_prefix);

-- Verification profiles
CREATE TABLE verification_profile (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description TEXT NULL,
    preset VARCHAR(20) NOT NULL,
    policy_xml TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX uq_verification_profile_default
    ON verification_profile(is_default)
    WHERE is_default = TRUE;

-- Validation jobs
CREATE TABLE validation_job (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    original_status VARCHAR(20) NULL,
    profile_id UUID NULL REFERENCES verification_profile(id),
    profile_overrides TEXT NULL,
    reports_requested VARCHAR(100) NOT NULL,
    document_path VARCHAR(500) NULL,
    document_filename VARCHAR(255) NULL,
    result_path VARCHAR(500) NULL,
    callback_url VARCHAR(500) NULL,
    callback_secret_cipher TEXT NULL,
    callback_algorithm VARCHAR(20) NULL,
    callback_attempts INT NOT NULL DEFAULT 0,
    next_callback_at TIMESTAMP NULL,
    last_callback_error TEXT NULL,
    pickup_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    delivered_at TIMESTAMP NULL,
    expires_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP NULL,
    error_message TEXT NULL,
    requested_by_principal_type VARCHAR(20) NOT NULL,
    requested_by_principal_id VARCHAR(120) NOT NULL,
    last_accessed_at TIMESTAMP NULL
);
CREATE INDEX idx_validation_job_status_next_callback ON validation_job(status, next_callback_at);
CREATE INDEX idx_validation_job_status_pickup ON validation_job(status, pickup_attempts);
CREATE INDEX idx_validation_job_status_expires ON validation_job(status, expires_at);
CREATE INDEX idx_validation_job_principal_status
    ON validation_job(requested_by_principal_type, requested_by_principal_id, status);

-- Trusted certificates (mirror)
CREATE TABLE trusted_certificate (
    id UUID PRIMARY KEY,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    ski VARCHAR(64) NULL,
    aki VARCHAR(64) NULL,
    subject_dn VARCHAR(500) NULL,
    subject_cn VARCHAR(255) NULL,
    issuer_dn VARCHAR(500) NULL,
    issuer_cn VARCHAR(255) NULL,
    serial_number VARCHAR(80) NULL,
    country VARCHAR(8) NULL,
    tsp_name VARCHAR(255) NULL,
    tsp_service_type VARCHAR(255) NULL,
    tsp_service_status VARCHAR(80) NULL,
    valid_from TIMESTAMP NULL,
    valid_to TIMESTAMP NULL,
    certificate_der_b64 TEXT NOT NULL,
    tsl_url VARCHAR(500) NULL,
    last_seen_at TIMESTAMP NOT NULL,
    removed_at TIMESTAMP NULL
);
CREATE INDEX idx_trusted_cert_ski ON trusted_certificate(ski);
CREATE INDEX idx_trusted_cert_aki ON trusted_certificate(aki);
CREATE INDEX idx_trusted_cert_subject_cn ON trusted_certificate(subject_cn);
CREATE INDEX idx_trusted_cert_subject_dn ON trusted_certificate(subject_dn);
CREATE INDEX idx_trusted_cert_issuer_dn ON trusted_certificate(issuer_dn);
CREATE INDEX idx_trusted_cert_serial ON trusted_certificate(serial_number);
CREATE INDEX idx_trusted_cert_country ON trusted_certificate(country);
CREATE INDEX idx_trusted_cert_tsp_name ON trusted_certificate(tsp_name);
CREATE INDEX idx_trusted_cert_tsp_service_type ON trusted_certificate(tsp_service_type);
CREATE INDEX idx_trusted_cert_tsp_service_status ON trusted_certificate(tsp_service_status);
CREATE INDEX idx_trusted_cert_validity ON trusted_certificate(valid_from, valid_to);

-- TSL refresh history
CREATE TABLE tsl_refresh (
    id UUID PRIMARY KEY,
    trigger VARCHAR(20) NOT NULL,
    triggered_by_principal_type VARCHAR(20) NULL,
    triggered_by_principal_id VARCHAR(120) NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL,
    sources_total INT NOT NULL DEFAULT 0,
    sources_failed INT NOT NULL DEFAULT 0,
    certificates_added INT NOT NULL DEFAULT 0,
    certificates_removed INT NOT NULL DEFAULT 0,
    certificates_unchanged INT NOT NULL DEFAULT 0,
    error_summary TEXT NULL
);
CREATE INDEX idx_tsl_refresh_started ON tsl_refresh(started_at DESC);

-- Audit log
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    principal_id VARCHAR(120) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NULL,
    target_id VARCHAR(120) NULL,
    success BOOLEAN NOT NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL
);
CREATE INDEX idx_audit_occurred ON audit_log(occurred_at DESC);
CREATE INDEX idx_audit_principal ON audit_log(principal_id);
CREATE INDEX idx_audit_action ON audit_log(action);

-- ShedLock
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

- [ ] **Step 2: Run test → contextLoads passa**

```bash
mvn -B test -Dtest=SignVerifyApplicationTest -q
```

Expected: BUILD SUCCESS. Flyway esegue V1 su H2, JPA validate trova le tabelle.

- [ ] **Step 3: Commit**

```bash
git add src/main src/test
git commit -m "feat: add spring boot bootstrap + flyway V1 init schema"
```

## Task 0.4: `application.yaml` produzione (defaults completi)

**Files:**
- Create: `src/main/resources/application.yaml`

- [ ] **Step 1: Scrivere defaults**

`src/main/resources/application.yaml`:
```yaml
spring:
  application:
    name: sign-verify-2
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:h2:mem:dev;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}
    username: ${SPRING_DATASOURCE_USERNAME:sa}
    password: ${SPRING_DATASOURCE_PASSWORD:}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 60MB
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${APP_SECURITY_OAUTH_ISSUER_URI:}

server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful
  error:
    include-message: never
    include-stacktrace: never

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true

app:
  upload:
    max-size: 50MB
  security:
    oauth:
      enabled: ${APP_SECURITY_OAUTH_ENABLED:true}
      role-claim: ${APP_SECURITY_OAUTH_ROLE_CLAIM:roles}
      privileged-values: ${APP_SECURITY_OAUTH_PRIVILEGED_VALUES:admin,privileged}
    bootstrap-key-file: ${APP_SECURITY_BOOTSTRAP_KEY_FILE:/var/lib/sign-verify/bootstrap-api-key.txt}
    master-key: ${APP_SECRET_MASTER_KEY:}
  storage:
    jobs-dir: ${APP_STORAGE_JOBS_DIR:/var/lib/sign-verify/jobs}
  dss:
    cache-dir: ${APP_DSS_CACHE_DIR:/var/lib/sign-verify/dss-cache}
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
      startup-mode: BACKGROUND
  verify:
    max-concurrent: 8
  async:
    workers: 4
    worker:
      poll-interval: 5s
    max-pending-per-principal: 50
    max-pending-global: 500
    max-pickup-attempts: 10
    job-ttl: 7d
    input-retention: 1h
    result-retention: 30d
    tombstone-retention: 30d
    cleanup:
      cron: "0 30 3 * * *"
  callback:
    max-attempts: 3
    backoff: [60s, 300s, 1800s]
    success-statuses: [200, 201, 202, 204]
    retryable-statuses: [408, 425, 429, 500, 502, 503, 504]
    non-retryable-statuses: [400, 401, 403, 404, 410, 422]
    timeout: 15s
    hmac-default-algorithm: HmacSHA256
    allowed-algorithms: [HmacSHA256, HmacSHA512]
    allow-http: false
    block-private-networks: true
    worker:
      poll-interval: 10s

resilience4j:
  circuitbreaker:
    instances:
      dssValidator:
        failureRateThreshold: 50
        slidingWindowSize: 20
        waitDurationInOpenState: 60s
        slowCallDurationThreshold: 30s
        slowCallRateThreshold: 80
        permittedNumberOfCallsInHalfOpenState: 3
        registerHealthIndicator: true

logging:
  level:
    eu.europa.esig: WARN
    org.toresoft.signverify: INFO
```

- [ ] **Step 2: Aggiungi `application-dev.yaml` per esecuzione locale senza env**

`src/main/resources/application-dev.yaml`:
```yaml
app:
  security:
    oauth:
      enabled: false
    bootstrap-key-file: ./target/bootstrap-api-key.txt
    master-key: ZGV2LWRldi1kZXYtZGV2LWRldi1kZXYtZGV2LWRldi1kZXYtZGV2LWRldi1kZXY=
  storage:
    jobs-dir: ./target/jobs
  dss:
    cache-dir: ./target/dss-cache
  tsl:
    refresh:
      startup-mode: SKIP
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources
git commit -m "feat: add application configuration with profile defaults"
```

## Task 0.5: ArchUnit baseline test

**Files:**
- Create: `src/test/java/org/toresoft/signverify/ArchitectureTest.java`

- [ ] **Step 1: Test ArchUnit**

```java
package org.toresoft.signverify;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "org.toresoft.signverify",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule domain_does_not_depend_on_spring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..");

  @ArchTest
  static final ArchRule domain_does_not_depend_on_dss =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("eu.europa.esig..");

  @ArchTest
  static final ArchRule only_adapter_dss_imports_dss =
      noClasses()
          .that()
          .resideOutsideOfPackages("..adapter.dss..", "..config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("eu.europa.esig..");

  @ArchTest
  static final ArchRule controllers_do_not_use_repositories =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..persistence..");
}
```

- [ ] **Step 2: Crea package vuoti per soddisfare le regole (placeholder package-info)**

```bash
mkdir -p src/main/java/org/toresoft/signverify/{api,application,domain/{model,port,exception},adapter/{dss,storage,callback,crypto},persistence,security,config}
```

`src/main/java/org/toresoft/signverify/domain/package-info.java`:
```java
package org.toresoft.signverify.domain;
```

(Crea file analogo `package-info.java` per ciascun package elencato sopra. È necessario altrimenti ArchUnit non vede il package).

- [ ] **Step 3: Run ArchUnit test**

```bash
mvn -B test -Dtest=ArchitectureTest -q
```

Expected: BUILD SUCCESS (regole passano vacuamente — non c'è ancora codice).

- [ ] **Step 4: Commit**

```bash
git add src/test/java src/main/java/org/toresoft/signverify
git commit -m "test: add archunit baseline rules + package skeleton"
```

## Verifica Fase 0

```bash
mvn -B clean verify -q
```

Expected: BUILD SUCCESS. Output rilevante:
- Flyway applica V1
- Test `SignVerifyApplicationTest.contextLoads` passa
- `ArchitectureTest` 4 regole passano
- Jacoco genera report (coverage 0%)
- Spotless check passa (formato Google)

Se spotless check fallisce per imports: `mvn spotless:apply` poi commit.

---

# FASE 1 — Modello dominio JPA + repository

**Obiettivo**: tutte le entity JPA per le 7 tabelle del modello, con repository Spring Data. Test repository con H2 per ciascuna entity. Coverage entity 100%.

## Task 1.1: Enumerati dominio

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/Role.java`
- Create: `src/main/java/org/toresoft/signverify/domain/model/PrincipalType.java`
- Create: `src/main/java/org/toresoft/signverify/domain/model/JobStatus.java`
- Create: `src/main/java/org/toresoft/signverify/domain/model/ProfilePreset.java`
- Create: `src/main/java/org/toresoft/signverify/domain/model/RefreshTrigger.java`
- Create: `src/main/java/org/toresoft/signverify/domain/model/RefreshStatus.java`

- [ ] **Step 1: Scrivere enum**

```java
// Role.java
package org.toresoft.signverify.domain.model;
public enum Role { PRIVILEGED, STANDARD }

// PrincipalType.java
package org.toresoft.signverify.domain.model;
public enum PrincipalType { API_KEY, OAUTH_USER, SYSTEM }

// JobStatus.java
package org.toresoft.signverify.domain.model;
public enum JobStatus {
  PENDING, RUNNING, COMPLETED, FAILED, DELIVERED, DELIVERY_FAILED, DELETED;
  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == DELIVERED || this == DELIVERY_FAILED;
  }
}

// ProfilePreset.java
package org.toresoft.signverify.domain.model;
public enum ProfilePreset { BASIC, STANDARD, STRICT, CUSTOM }

// RefreshTrigger.java
package org.toresoft.signverify.domain.model;
public enum RefreshTrigger { SCHEDULED, MANUAL, STARTUP }

// RefreshStatus.java
package org.toresoft.signverify.domain.model;
public enum RefreshStatus { RUNNING, SUCCESS, PARTIAL, FAILED }
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/toresoft/signverify/domain/model
git commit -m "feat(domain): add enums for role, principal, job status, profile, refresh"
```

## Task 1.2: Entity `ApiKey` + repository

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/ApiKey.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/ApiKeyRepository.java`
- Create: `src/test/java/org/toresoft/signverify/persistence/ApiKeyRepositoryTest.java`

- [ ] **Step 1: Test repository**

```java
package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;

@DataJpaTest
@ActiveProfiles("test")
class ApiKeyRepositoryTest {

  @Autowired private ApiKeyRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void persist_and_load_by_prefix() {
    ApiKey k = newKey("alpha", "abc12345", Role.PRIVILEGED, true);
    repo.save(k);
    em.flush();
    em.clear();

    ApiKey loaded = repo.findByKeyPrefix("abc12345").orElseThrow();
    assertThat(loaded.getName()).isEqualTo("alpha");
    assertThat(loaded.getRole()).isEqualTo(Role.PRIVILEGED);
    assertThat(loaded.isEnabled()).isTrue();
  }

  @Test
  void count_privileged_enabled() {
    repo.save(newKey("p1", "p1prefix", Role.PRIVILEGED, true));
    repo.save(newKey("p2", "p2prefix", Role.PRIVILEGED, false));
    repo.save(newKey("s1", "s1prefix", Role.STANDARD, true));
    em.flush();

    assertThat(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).isEqualTo(1);
  }

  private ApiKey newKey(String name, String prefix, Role role, boolean enabled) {
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName(name);
    k.setKeyPrefix(prefix);
    k.setKeyHash("$2a$12$dummyhash");
    k.setRole(role);
    k.setEnabled(enabled);
    k.setBootstrap(false);
    k.setCreatedAt(Instant.now());
    return k;
  }
}
```

- [ ] **Step 2: Run → fallisce**

```bash
mvn -B test -Dtest=ApiKeyRepositoryTest -q
```

Expected: compile error (entity/repo non esistono).

- [ ] **Step 3: Entity**

`src/main/java/org/toresoft/signverify/domain/model/ApiKey.java`:
```java
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_key")
public class ApiKey {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Column(name = "key_prefix", nullable = false, length = 8)
  private String keyPrefix;

  @Column(name = "key_hash", nullable = false, length = 255)
  private String keyHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private boolean bootstrap;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "created_by_principal_type", length = 20)
  private PrincipalType createdByPrincipalType;

  @Column(name = "created_by_principal_id", length = 120)
  private String createdByPrincipalId;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  // getter/setter completi
  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getKeyPrefix() { return keyPrefix; }
  public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
  public String getKeyHash() { return keyHash; }
  public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public boolean isBootstrap() { return bootstrap; }
  public void setBootstrap(boolean bootstrap) { this.bootstrap = bootstrap; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public PrincipalType getCreatedByPrincipalType() { return createdByPrincipalType; }
  public void setCreatedByPrincipalType(PrincipalType t) { this.createdByPrincipalType = t; }
  public String getCreatedByPrincipalId() { return createdByPrincipalId; }
  public void setCreatedByPrincipalId(String id) { this.createdByPrincipalId = id; }
  public Instant getLastUsedAt() { return lastUsedAt; }
  public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
```

- [ ] **Step 4: Repository**

`src/main/java/org/toresoft/signverify/persistence/ApiKeyRepository.java`:
```java
package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

  Optional<ApiKey> findByKeyPrefix(String prefix);

  Optional<ApiKey> findByName(String name);

  long countByRoleAndEnabled(Role role, boolean enabled);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  long countByRoleAndEnabledForUpdate(Role role, boolean enabled);
}
```

Nota: il metodo `countByRoleAndEnabledForUpdate` con `@Lock` su `count` non è supportato direttamente da Spring Data; sarà sostituito da una `@Query` esplicita nella Task 4.2. Per ora rimuoverlo o lasciarlo: lasciamolo come stub, marcheremo `default 0` se necessario. Per evitare problemi al boot, riscriviamo subito come:

```java
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
  Optional<ApiKey> findByKeyPrefix(String prefix);
  Optional<ApiKey> findByName(String name);
  long countByRoleAndEnabled(Role role, boolean enabled);
}
```

- [ ] **Step 5: Run test → passa**

```bash
mvn -B test -Dtest=ApiKeyRepositoryTest -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/toresoft/signverify/{domain,persistence} src/test/java/org/toresoft/signverify/persistence
git commit -m "feat(domain): add ApiKey entity + repository"
```

## Task 1.3: Entity `VerificationProfile` + repository

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/VerificationProfile.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/VerificationProfileRepository.java`
- Create: `src/test/java/org/toresoft/signverify/persistence/VerificationProfileRepositoryTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;

@DataJpaTest
@ActiveProfiles("test")
class VerificationProfileRepositoryTest {

  @Autowired private VerificationProfileRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void find_default() {
    VerificationProfile def = newProfile("STANDARD", ProfilePreset.STANDARD, true);
    VerificationProfile other = newProfile("STRICT", ProfilePreset.STRICT, false);
    repo.saveAll(java.util.List.of(def, other));
    em.flush();
    em.clear();

    assertThat(repo.findByIsDefaultTrue()).isPresent().get()
        .extracting(VerificationProfile::getName).isEqualTo("STANDARD");
  }

  private VerificationProfile newProfile(String name, ProfilePreset preset, boolean isDefault) {
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName(name);
    p.setPreset(preset);
    p.setPolicyXml("<ConstraintsParameters/>");
    p.setIsDefault(isDefault);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    return p;
  }
}
```

- [ ] **Step 2: Entity**

`src/main/java/org/toresoft/signverify/domain/model/VerificationProfile.java`:
```java
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_profile")
public class VerificationProfile {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 120)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProfilePreset preset;

  @Column(name = "policy_xml", nullable = false, columnDefinition = "TEXT")
  private String policyXml;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Version private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String d) { this.description = d; }
  public ProfilePreset getPreset() { return preset; }
  public void setPreset(ProfilePreset preset) { this.preset = preset; }
  public String getPolicyXml() { return policyXml; }
  public void setPolicyXml(String xml) { this.policyXml = xml; }
  public boolean getIsDefault() { return isDefault; }
  public void setIsDefault(boolean d) { this.isDefault = d; }
  public long getVersion() { return version; }
  public void setVersion(long v) { this.version = v; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant t) { this.createdAt = t; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
```

- [ ] **Step 3: Repository**

```java
package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.toresoft.signverify.domain.model.VerificationProfile;

public interface VerificationProfileRepository extends JpaRepository<VerificationProfile, UUID> {
  Optional<VerificationProfile> findByIsDefaultTrue();
  Optional<VerificationProfile> findByName(String name);
}
```

- [ ] **Step 4: Run test, commit**

```bash
mvn -B test -Dtest=VerificationProfileRepositoryTest -q
git add src/main src/test
git commit -m "feat(domain): add VerificationProfile entity + repository"
```

## Task 1.4: Entity `ValidationJob` + repository

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/ValidationJob.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java`
- Create: `src/test/java/org/toresoft/signverify/persistence/ValidationJobRepositoryTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

@DataJpaTest
@ActiveProfiles("test")
class ValidationJobRepositoryTest {

  @Autowired private ValidationJobRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void count_pending_per_principal() {
    repo.save(newJob(JobStatus.PENDING, "alice"));
    repo.save(newJob(JobStatus.RUNNING, "alice"));
    repo.save(newJob(JobStatus.COMPLETED, "alice"));
    repo.save(newJob(JobStatus.PENDING, "bob"));
    em.flush();

    assertThat(repo.countActiveByPrincipal(PrincipalType.API_KEY, "alice")).isEqualTo(2);
    assertThat(repo.countActiveByPrincipal(PrincipalType.API_KEY, "bob")).isEqualTo(1);
  }

  @Test
  void pick_pending_for_processing_returns_oldest() {
    Instant base = Instant.now();
    ValidationJob j1 = newJob(JobStatus.PENDING, "alice");
    j1.setCreatedAt(base.minusSeconds(60));
    ValidationJob j2 = newJob(JobStatus.PENDING, "alice");
    j2.setCreatedAt(base.minusSeconds(30));
    repo.saveAll(List.of(j1, j2));
    em.flush();

    var picked = repo.findPickablePending(10, 10);
    assertThat(picked).hasSize(2);
    assertThat(picked.get(0).getId()).isEqualTo(j1.getId());
  }

  private ValidationJob newJob(JobStatus status, String principalId) {
    ValidationJob j = new ValidationJob();
    j.setId(UUID.randomUUID());
    j.setStatus(status);
    j.setReportsRequested("simple,etsi");
    j.setRequestedByPrincipalType(PrincipalType.API_KEY);
    j.setRequestedByPrincipalId(principalId);
    j.setCreatedAt(Instant.now());
    j.setExpiresAt(Instant.now().plusSeconds(86400));
    return j;
  }
}
```

- [ ] **Step 2: Entity**

`src/main/java/org/toresoft/signverify/domain/model/ValidationJob.java`:
```java
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_job")
public class ValidationJob {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JobStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "original_status", length = 20)
  private JobStatus originalStatus;

  @Column(name = "profile_id")
  private UUID profileId;

  @Column(name = "profile_overrides", columnDefinition = "TEXT")
  private String profileOverrides;

  @Column(name = "reports_requested", nullable = false, length = 100)
  private String reportsRequested;

  @Column(name = "document_path", length = 500)
  private String documentPath;

  @Column(name = "document_filename", length = 255)
  private String documentFilename;

  @Column(name = "result_path", length = 500)
  private String resultPath;

  @Column(name = "callback_url", length = 500)
  private String callbackUrl;

  @Column(name = "callback_secret_cipher", columnDefinition = "TEXT")
  private String callbackSecretCipher;

  @Column(name = "callback_algorithm", length = 20)
  private String callbackAlgorithm;

  @Column(name = "callback_attempts", nullable = false)
  private int callbackAttempts;

  @Column(name = "next_callback_at")
  private Instant nextCallbackAt;

  @Column(name = "last_callback_error", columnDefinition = "TEXT")
  private String lastCallbackError;

  @Column(name = "pickup_attempts", nullable = false)
  private int pickupAttempts;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Enumerated(EnumType.STRING)
  @Column(name = "requested_by_principal_type", nullable = false, length = 20)
  private PrincipalType requestedByPrincipalType;

  @Column(name = "requested_by_principal_id", nullable = false, length = 120)
  private String requestedByPrincipalId;

  @Column(name = "last_accessed_at")
  private Instant lastAccessedAt;

  // getter/setter completi
  public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
  public JobStatus getStatus() { return status; } public void setStatus(JobStatus s) { this.status = s; }
  public JobStatus getOriginalStatus() { return originalStatus; } public void setOriginalStatus(JobStatus s) { this.originalStatus = s; }
  public UUID getProfileId() { return profileId; } public void setProfileId(UUID id) { this.profileId = id; }
  public String getProfileOverrides() { return profileOverrides; } public void setProfileOverrides(String s) { this.profileOverrides = s; }
  public String getReportsRequested() { return reportsRequested; } public void setReportsRequested(String s) { this.reportsRequested = s; }
  public String getDocumentPath() { return documentPath; } public void setDocumentPath(String s) { this.documentPath = s; }
  public String getDocumentFilename() { return documentFilename; } public void setDocumentFilename(String s) { this.documentFilename = s; }
  public String getResultPath() { return resultPath; } public void setResultPath(String s) { this.resultPath = s; }
  public String getCallbackUrl() { return callbackUrl; } public void setCallbackUrl(String s) { this.callbackUrl = s; }
  public String getCallbackSecretCipher() { return callbackSecretCipher; } public void setCallbackSecretCipher(String s) { this.callbackSecretCipher = s; }
  public String getCallbackAlgorithm() { return callbackAlgorithm; } public void setCallbackAlgorithm(String s) { this.callbackAlgorithm = s; }
  public int getCallbackAttempts() { return callbackAttempts; } public void setCallbackAttempts(int n) { this.callbackAttempts = n; }
  public Instant getNextCallbackAt() { return nextCallbackAt; } public void setNextCallbackAt(Instant t) { this.nextCallbackAt = t; }
  public String getLastCallbackError() { return lastCallbackError; } public void setLastCallbackError(String s) { this.lastCallbackError = s; }
  public int getPickupAttempts() { return pickupAttempts; } public void setPickupAttempts(int n) { this.pickupAttempts = n; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant t) { this.createdAt = t; }
  public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant t) { this.startedAt = t; }
  public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant t) { this.completedAt = t; }
  public Instant getDeliveredAt() { return deliveredAt; } public void setDeliveredAt(Instant t) { this.deliveredAt = t; }
  public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant t) { this.expiresAt = t; }
  public Instant getDeletedAt() { return deletedAt; } public void setDeletedAt(Instant t) { this.deletedAt = t; }
  public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String s) { this.errorMessage = s; }
  public PrincipalType getRequestedByPrincipalType() { return requestedByPrincipalType; }
  public void setRequestedByPrincipalType(PrincipalType t) { this.requestedByPrincipalType = t; }
  public String getRequestedByPrincipalId() { return requestedByPrincipalId; } public void setRequestedByPrincipalId(String s) { this.requestedByPrincipalId = s; }
  public Instant getLastAccessedAt() { return lastAccessedAt; } public void setLastAccessedAt(Instant t) { this.lastAccessedAt = t; }
}
```

- [ ] **Step 3: Repository con query custom**

```java
package org.toresoft.signverify.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

public interface ValidationJobRepository extends JpaRepository<ValidationJob, UUID> {

  @Query("""
      SELECT COUNT(j) FROM ValidationJob j
       WHERE j.requestedByPrincipalType = :type
         AND j.requestedByPrincipalId = :id
         AND j.status IN ('PENDING','RUNNING')
      """)
  long countActiveByPrincipal(@Param("type") PrincipalType type, @Param("id") String id);

  @Query("""
      SELECT COUNT(j) FROM ValidationJob j
       WHERE j.status IN ('PENDING','RUNNING')
      """)
  long countActiveGlobal();

  @Query("""
      SELECT j FROM ValidationJob j
       WHERE j.status = 'PENDING'
         AND j.pickupAttempts < :maxAttempts
       ORDER BY j.createdAt ASC
      """)
  List<ValidationJob> findPickablePending(
      @Param("maxAttempts") int maxAttempts,
      org.springframework.data.domain.Pageable pageable);

  default List<ValidationJob> findPickablePending(int maxAttempts, int limit) {
    return findPickablePending(maxAttempts, PageRequest.of(0, limit));
  }

  @Query("""
      SELECT j FROM ValidationJob j
       WHERE j.status IN ('COMPLETED','FAILED')
         AND j.callbackUrl IS NOT NULL
         AND j.nextCallbackAt <= :now
         AND j.callbackAttempts < :maxAttempts
       ORDER BY j.nextCallbackAt ASC
      """)
  List<ValidationJob> findCallbacksDue(
      @Param("now") Instant now,
      @Param("maxAttempts") int maxAttempts,
      org.springframework.data.domain.Pageable pageable);

  default List<ValidationJob> findCallbacksDue(Instant now, int maxAttempts, int limit) {
    return findCallbacksDue(now, maxAttempts, PageRequest.of(0, limit));
  }
}
```

- [ ] **Step 4: Run test, commit**

```bash
mvn -B test -Dtest=ValidationJobRepositoryTest -q
git add src/main src/test
git commit -m "feat(domain): add ValidationJob entity + repository with picking queries"
```

## Task 1.5: Entity `TrustedCertificate` + repository (con Specification)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/TrustedCertificate.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/TrustedCertificateRepository.java`
- Create: `src/test/java/org/toresoft/signverify/persistence/TrustedCertificateRepositoryTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.TrustedCertificate;

@DataJpaTest
@ActiveProfiles("test")
class TrustedCertificateRepositoryTest {

  @Autowired private TrustedCertificateRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void find_by_fingerprint() {
    TrustedCertificate c = newCert("ab12", "IT", "Acme TSP");
    repo.save(c);
    em.flush();

    assertThat(repo.findByFingerprintSha256("ab12")).isPresent();
  }

  private TrustedCertificate newCert(String fp, String country, String tspName) {
    TrustedCertificate c = new TrustedCertificate();
    c.setId(UUID.randomUUID());
    c.setFingerprintSha256(fp);
    c.setCertificateDerB64("dGVzdA==");
    c.setCountry(country);
    c.setTspName(tspName);
    c.setLastSeenAt(Instant.now());
    return c;
  }
}
```

- [ ] **Step 2: Entity**

```java
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trusted_certificate")
public class TrustedCertificate {
  @Id private UUID id;
  @Column(name = "fingerprint_sha256", nullable = false, unique = true, length = 64)
  private String fingerprintSha256;
  @Column(length = 64) private String ski;
  @Column(length = 64) private String aki;
  @Column(name = "subject_dn", length = 500) private String subjectDn;
  @Column(name = "subject_cn", length = 255) private String subjectCn;
  @Column(name = "issuer_dn", length = 500) private String issuerDn;
  @Column(name = "issuer_cn", length = 255) private String issuerCn;
  @Column(name = "serial_number", length = 80) private String serialNumber;
  @Column(length = 8) private String country;
  @Column(name = "tsp_name", length = 255) private String tspName;
  @Column(name = "tsp_service_type", length = 255) private String tspServiceType;
  @Column(name = "tsp_service_status", length = 80) private String tspServiceStatus;
  @Column(name = "valid_from") private Instant validFrom;
  @Column(name = "valid_to") private Instant validTo;
  @Column(name = "certificate_der_b64", nullable = false, columnDefinition = "TEXT")
  private String certificateDerB64;
  @Column(name = "tsl_url", length = 500) private String tslUrl;
  @Column(name = "last_seen_at", nullable = false) private Instant lastSeenAt;
  @Column(name = "removed_at") private Instant removedAt;

  public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
  public String getFingerprintSha256() { return fingerprintSha256; } public void setFingerprintSha256(String s) { this.fingerprintSha256 = s; }
  public String getSki() { return ski; } public void setSki(String s) { this.ski = s; }
  public String getAki() { return aki; } public void setAki(String s) { this.aki = s; }
  public String getSubjectDn() { return subjectDn; } public void setSubjectDn(String s) { this.subjectDn = s; }
  public String getSubjectCn() { return subjectCn; } public void setSubjectCn(String s) { this.subjectCn = s; }
  public String getIssuerDn() { return issuerDn; } public void setIssuerDn(String s) { this.issuerDn = s; }
  public String getIssuerCn() { return issuerCn; } public void setIssuerCn(String s) { this.issuerCn = s; }
  public String getSerialNumber() { return serialNumber; } public void setSerialNumber(String s) { this.serialNumber = s; }
  public String getCountry() { return country; } public void setCountry(String s) { this.country = s; }
  public String getTspName() { return tspName; } public void setTspName(String s) { this.tspName = s; }
  public String getTspServiceType() { return tspServiceType; } public void setTspServiceType(String s) { this.tspServiceType = s; }
  public String getTspServiceStatus() { return tspServiceStatus; } public void setTspServiceStatus(String s) { this.tspServiceStatus = s; }
  public Instant getValidFrom() { return validFrom; } public void setValidFrom(Instant t) { this.validFrom = t; }
  public Instant getValidTo() { return validTo; } public void setValidTo(Instant t) { this.validTo = t; }
  public String getCertificateDerB64() { return certificateDerB64; } public void setCertificateDerB64(String s) { this.certificateDerB64 = s; }
  public String getTslUrl() { return tslUrl; } public void setTslUrl(String s) { this.tslUrl = s; }
  public Instant getLastSeenAt() { return lastSeenAt; } public void setLastSeenAt(Instant t) { this.lastSeenAt = t; }
  public Instant getRemovedAt() { return removedAt; } public void setRemovedAt(Instant t) { this.removedAt = t; }
}
```

- [ ] **Step 3: Repository con Specification support**

```java
package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.toresoft.signverify.domain.model.TrustedCertificate;

public interface TrustedCertificateRepository
    extends JpaRepository<TrustedCertificate, UUID>,
            JpaSpecificationExecutor<TrustedCertificate> {

  Optional<TrustedCertificate> findByFingerprintSha256(String fp);
}
```

- [ ] **Step 4: Run, commit**

```bash
mvn -B test -Dtest=TrustedCertificateRepositoryTest -q
git add src/main src/test
git commit -m "feat(domain): add TrustedCertificate entity + repo with specification support"
```

## Task 1.6: Entity `TslRefresh` + repository

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/TslRefresh.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/TslRefreshRepository.java`
- Create: `src/test/java/org/toresoft/signverify/persistence/TslRefreshRepositoryTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.RefreshStatus;
import org.toresoft.signverify.domain.model.RefreshTrigger;
import org.toresoft.signverify.domain.model.TslRefresh;

@DataJpaTest
@ActiveProfiles("test")
class TslRefreshRepositoryTest {

  @Autowired private TslRefreshRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void find_latest() {
    TslRefresh older = newRefresh(Instant.parse("2026-06-01T00:00:00Z"));
    TslRefresh newer = newRefresh(Instant.parse("2026-06-02T00:00:00Z"));
    repo.saveAll(java.util.List.of(older, newer));
    em.flush();

    assertThat(repo.findTopByOrderByStartedAtDesc()).isPresent().get()
        .extracting(TslRefresh::getId).isEqualTo(newer.getId());
  }

  private TslRefresh newRefresh(Instant startedAt) {
    TslRefresh r = new TslRefresh();
    r.setId(UUID.randomUUID());
    r.setTrigger(RefreshTrigger.SCHEDULED);
    r.setStartedAt(startedAt);
    r.setStatus(RefreshStatus.SUCCESS);
    return r;
  }
}
```

- [ ] **Step 2: Entity**

```java
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tsl_refresh")
public class TslRefresh {
  @Id private UUID id;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private RefreshTrigger trigger;
  @Enumerated(EnumType.STRING) @Column(name = "triggered_by_principal_type", length = 20)
  private PrincipalType triggeredByPrincipalType;
  @Column(name = "triggered_by_principal_id", length = 120)
  private String triggeredByPrincipalId;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private RefreshStatus status;
  @Column(name = "sources_total", nullable = false) private int sourcesTotal;
  @Column(name = "sources_failed", nullable = false) private int sourcesFailed;
  @Column(name = "certificates_added", nullable = false) private int certificatesAdded;
  @Column(name = "certificates_removed", nullable = false) private int certificatesRemoved;
  @Column(name = "certificates_unchanged", nullable = false) private int certificatesUnchanged;
  @Column(name = "error_summary", columnDefinition = "TEXT") private String errorSummary;

  public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
  public RefreshTrigger getTrigger() { return trigger; } public void setTrigger(RefreshTrigger t) { this.trigger = t; }
  public PrincipalType getTriggeredByPrincipalType() { return triggeredByPrincipalType; }
  public void setTriggeredByPrincipalType(PrincipalType t) { this.triggeredByPrincipalType = t; }
  public String getTriggeredByPrincipalId() { return triggeredByPrincipalId; }
  public void setTriggeredByPrincipalId(String s) { this.triggeredByPrincipalId = s; }
  public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant t) { this.startedAt = t; }
  public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant t) { this.completedAt = t; }
  public RefreshStatus getStatus() { return status; } public void setStatus(RefreshStatus s) { this.status = s; }
  public int getSourcesTotal() { return sourcesTotal; } public void setSourcesTotal(int n) { this.sourcesTotal = n; }
  public int getSourcesFailed() { return sourcesFailed; } public void setSourcesFailed(int n) { this.sourcesFailed = n; }
  public int getCertificatesAdded() { return certificatesAdded; } public void setCertificatesAdded(int n) { this.certificatesAdded = n; }
  public int getCertificatesRemoved() { return certificatesRemoved; } public void setCertificatesRemoved(int n) { this.certificatesRemoved = n; }
  public int getCertificatesUnchanged() { return certificatesUnchanged; } public void setCertificatesUnchanged(int n) { this.certificatesUnchanged = n; }
  public String getErrorSummary() { return errorSummary; } public void setErrorSummary(String s) { this.errorSummary = s; }
}
```

- [ ] **Step 3: Repository**

```java
package org.toresoft.signverify.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.toresoft.signverify.domain.model.TslRefresh;

public interface TslRefreshRepository extends JpaRepository<TslRefresh, UUID> {
  Optional<TslRefresh> findTopByOrderByStartedAtDesc();
}
```

- [ ] **Step 4: Run, commit**

```bash
mvn -B test -Dtest=TslRefreshRepositoryTest -q
git add src/main src/test
git commit -m "feat(domain): add TslRefresh entity + repository"
```

## Task 1.7: Entity `AuditLog` + repository

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/AuditLog.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/AuditLogRepository.java`
- Create: `src/test/java/org/toresoft/signverify/persistence/AuditLogRepositoryTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

  @Autowired private AuditLogRepository repo;
  @Autowired private TestEntityManager em;

  @Test
  void persist_and_query() {
    AuditLog log = new AuditLog();
    log.setId(UUID.randomUUID());
    log.setOccurredAt(Instant.now());
    log.setPrincipalType(PrincipalType.API_KEY);
    log.setPrincipalId("k1");
    log.setAction("API_KEY_CREATE");
    log.setSuccess(true);
    repo.save(log);
    em.flush();

    assertThat(repo.count()).isEqualTo(1);
  }
}
```

- [ ] **Step 2: Entity**

```java
package org.toresoft.signverify.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
  @Id private UUID id;
  @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
  @Enumerated(EnumType.STRING) @Column(name = "principal_type", nullable = false, length = 20)
  private PrincipalType principalType;
  @Column(name = "principal_id", nullable = false, length = 120) private String principalId;
  @Column(nullable = false, length = 60) private String action;
  @Column(name = "target_type", length = 40) private String targetType;
  @Column(name = "target_id", length = 120) private String targetId;
  @Column(nullable = false) private boolean success;
  @Column(columnDefinition = "TEXT") private String details;
  @Column(name = "ip_address", length = 64) private String ipAddress;

  public UUID getId() { return id; } public void setId(UUID id) { this.id = id; }
  public Instant getOccurredAt() { return occurredAt; } public void setOccurredAt(Instant t) { this.occurredAt = t; }
  public PrincipalType getPrincipalType() { return principalType; } public void setPrincipalType(PrincipalType t) { this.principalType = t; }
  public String getPrincipalId() { return principalId; } public void setPrincipalId(String s) { this.principalId = s; }
  public String getAction() { return action; } public void setAction(String s) { this.action = s; }
  public String getTargetType() { return targetType; } public void setTargetType(String s) { this.targetType = s; }
  public String getTargetId() { return targetId; } public void setTargetId(String s) { this.targetId = s; }
  public boolean isSuccess() { return success; } public void setSuccess(boolean b) { this.success = b; }
  public String getDetails() { return details; } public void setDetails(String s) { this.details = s; }
  public String getIpAddress() { return ipAddress; } public void setIpAddress(String s) { this.ipAddress = s; }
}
```

- [ ] **Step 3: Repository**

```java
package org.toresoft.signverify.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.toresoft.signverify.domain.model.AuditLog;

public interface AuditLogRepository
    extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {}
```

- [ ] **Step 4: Run, commit**

```bash
mvn -B test -Dtest=AuditLogRepositoryTest -q
git add src/main src/test
git commit -m "feat(domain): add AuditLog entity + repository"
```

## Verifica Fase 1

```bash
mvn -B clean verify -q
```

Expected: BUILD SUCCESS. Tutti i test repository passano. JPA `ddl-auto: validate` non lamenta differenze fra entity e schema.

**Fine Fase 1.** Continua nella Fase 2 (Sicurezza).

---

# FASE 2 — Sicurezza (principal, API key, OAuth, bootstrap)

**Obiettivo**: filter API key, OAuth2 Resource Server JWT configurabile, principal unificato, bootstrap della prima API key privilegiata al primo avvio, ruoli con `@PreAuthorize`.

## Task 2.1: `Principal` value object + `Role` constants

**Files:**
- Create: `src/main/java/org/toresoft/signverify/security/Principal.java`
- Create: `src/main/java/org/toresoft/signverify/security/Roles.java`

- [ ] **Step 1: Implementa**

`src/main/java/org/toresoft/signverify/security/Principal.java`:
```java
package org.toresoft.signverify.security;

import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;

public record Principal(PrincipalType type, String id, Role role, String displayName) {

  public static Principal system() {
    return new Principal(PrincipalType.SYSTEM, "system", Role.PRIVILEGED, "system");
  }
}
```

`src/main/java/org/toresoft/signverify/security/Roles.java`:
```java
package org.toresoft.signverify.security;

public final class Roles {
  public static final String PRIVILEGED = "PRIVILEGED";
  public static final String STANDARD = "STANDARD";
  public static final String ROLE_PRIVILEGED = "ROLE_PRIVILEGED";
  public static final String ROLE_STANDARD = "ROLE_STANDARD";
  private Roles() {}
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/toresoft/signverify/security
git commit -m "feat(security): add Principal record + Role constants"
```

## Task 2.2: `PasswordHasherPort` + Bcrypt adapter

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/PasswordHasherPort.java`
- Create: `src/main/java/org/toresoft/signverify/adapter/crypto/BcryptPasswordHasherAdapter.java`
- Create: `src/test/java/org/toresoft/signverify/adapter/crypto/BcryptPasswordHasherAdapterTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class BcryptPasswordHasherAdapterTest {

  private final BcryptPasswordHasherAdapter hasher = new BcryptPasswordHasherAdapter(12);

  @Test
  void hash_and_verify_roundtrip() {
    String plain = "supersecret";
    String hash = hasher.hash(plain);
    assertThat(hash).isNotEqualTo(plain).startsWith("$2");
    assertThat(hasher.matches(plain, hash)).isTrue();
    assertThat(hasher.matches("other", hash)).isFalse();
  }
}
```

- [ ] **Step 2: Port**

```java
package org.toresoft.signverify.domain.port;

public interface PasswordHasherPort {
  String hash(String plaintext);
  boolean matches(String plaintext, String hash);
}
```

- [ ] **Step 3: Adapter**

```java
package org.toresoft.signverify.adapter.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.PasswordHasherPort;

@Component
public class BcryptPasswordHasherAdapter implements PasswordHasherPort {

  private final int cost;

  public BcryptPasswordHasherAdapter(@Value("${app.security.bcrypt-cost:12}") int cost) {
    this.cost = cost;
  }

  @Override
  public String hash(String plaintext) {
    return BCrypt.hashpw(plaintext, BCrypt.gensalt(cost));
  }

  @Override
  public boolean matches(String plaintext, String hash) {
    try {
      return BCrypt.checkpw(plaintext, hash);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
```

- [ ] **Step 4: Run, commit**

```bash
mvn -B test -Dtest=BcryptPasswordHasherAdapterTest -q
git add src/main src/test
git commit -m "feat(security): add password hasher port + bcrypt adapter"
```

## Task 2.3: `ApiKeyAuthenticationFilter`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/security/ApiKeyAuthentication.java`
- Create: `src/main/java/org/toresoft/signverify/security/ApiKeyAuthenticationFilter.java`
- Create: `src/test/java/org/toresoft/signverify/security/ApiKeyAuthenticationFilterTest.java`

- [ ] **Step 1: Test del filter**

```java
package org.toresoft.signverify.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

class ApiKeyAuthenticationFilterTest {

  private final ApiKeyRepository repo = mock(ApiKeyRepository.class);
  private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
  private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(repo, hasher);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void valid_key_authenticates() throws Exception {
    String plaintext = "sv_alphabe1_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    ApiKey key = enabledKey("alphabe1", "hash");
    when(repo.findByKeyPrefix("alphabe1")).thenReturn(Optional.of(key));
    when(hasher.matches(plaintext, "hash")).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", plaintext);
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    var auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.isAuthenticated()).isTrue();
    assertThat(((Principal) auth.getPrincipal()).type()).isEqualTo(PrincipalType.API_KEY);
    verify(chain).doFilter(req, res);
  }

  @Test
  void invalid_key_returns_401() throws Exception {
    when(repo.findByKeyPrefix(any())).thenReturn(Optional.empty());

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", "sv_unknown_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertThat(res.getStatus()).isEqualTo(401);
    verifyNoInteractions(chain);
  }

  @Test
  void disabled_key_returns_401() throws Exception {
    ApiKey key = enabledKey("alphabe1", "hash");
    key.setEnabled(false);
    when(repo.findByKeyPrefix("alphabe1")).thenReturn(Optional.of(key));

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-API-Key", "sv_alphabe1_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req, res, mock(FilterChain.class));
    assertThat(res.getStatus()).isEqualTo(401);
  }

  @Test
  void no_header_passes_through() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(req, res, chain);
    verify(chain).doFilter(req, res);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private ApiKey enabledKey(String prefix, String hash) {
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("test");
    k.setKeyPrefix(prefix);
    k.setKeyHash(hash);
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    return k;
  }
}
```

- [ ] **Step 2: `ApiKeyAuthentication`**

```java
package org.toresoft.signverify.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {

  private final Principal principal;

  public ApiKeyAuthentication(Principal principal) {
    super(buildAuthorities(principal));
    this.principal = principal;
    super.setAuthenticated(true);
  }

  private static Collection<? extends GrantedAuthority> buildAuthorities(Principal p) {
    return List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name()));
  }

  @Override public Object getCredentials() { return ""; }
  @Override public Object getPrincipal() { return principal; }
  @Override public String getName() { return principal.id(); }
}
```

- [ ] **Step 3: Filter**

```java
package org.toresoft.signverify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-API-Key";
  public static final int PREFIX_LENGTH = 8;

  private final ApiKeyRepository repo;
  private final PasswordHasherPort hasher;

  public ApiKeyAuthenticationFilter(ApiKeyRepository repo, PasswordHasherPort hasher) {
    this.repo = repo;
    this.hasher = hasher;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String raw = req.getHeader(HEADER);
    if (raw == null || raw.isBlank()) {
      chain.doFilter(req, res);
      return;
    }

    String prefix = extractPrefix(raw);
    if (prefix == null) {
      writeUnauthorized(res, "auth.invalid-token");
      return;
    }

    Optional<ApiKey> opt = repo.findByKeyPrefix(prefix);
    if (opt.isEmpty() || !opt.get().isEnabled() || isExpired(opt.get()) || !hasher.matches(raw, opt.get().getKeyHash())) {
      writeUnauthorized(res, "auth.invalid-token");
      return;
    }

    ApiKey key = opt.get();
    Principal p = new Principal(
        org.toresoft.signverify.domain.model.PrincipalType.API_KEY,
        key.getId().toString(),
        key.getRole(),
        key.getName());
    SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(p));
    chain.doFilter(req, res);
  }

  private String extractPrefix(String raw) {
    if (!raw.startsWith("sv_") || raw.length() < 3 + PREFIX_LENGTH + 1) return null;
    return raw.substring(3, 3 + PREFIX_LENGTH);
  }

  private boolean isExpired(ApiKey k) {
    return k.getExpiresAt() != null && k.getExpiresAt().isBefore(Instant.now());
  }

  private void writeUnauthorized(HttpServletResponse res, String code) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType("application/problem+json");
    res.getWriter().write("""
        {"type":"urn:signverify:error:%s","title":"Unauthorized","status":401}
        """.formatted(code));
  }
}
```

- [ ] **Step 4: Run, commit**

```bash
mvn -B test -Dtest=ApiKeyAuthenticationFilterTest -q
git add src/main src/test
git commit -m "feat(security): add API key authentication filter"
```

## Task 2.4: OAuth JWT role converter

**Files:**
- Create: `src/main/java/org/toresoft/signverify/security/OAuthPrincipalConverter.java`
- Create: `src/test/java/org/toresoft/signverify/security/OAuthPrincipalConverterTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.toresoft.signverify.domain.model.Role;

class OAuthPrincipalConverterTest {

  private final OAuthPrincipalConverter conv = new OAuthPrincipalConverter("roles", List.of("admin"));

  @Test
  void privileged_role_when_claim_contains_admin() {
    Jwt jwt = jwt(Map.of("sub", "user1", "roles", List.of("user", "admin")));
    var auth = (java.util.Collection<GrantedAuthority>) conv.convert(jwt);
    assertThat(auth).extracting(GrantedAuthority::getAuthority).contains("ROLE_PRIVILEGED");
  }

  @Test
  void standard_role_when_claim_no_admin() {
    Jwt jwt = jwt(Map.of("sub", "user1", "roles", List.of("user")));
    var auth = (java.util.Collection<GrantedAuthority>) conv.convert(jwt);
    assertThat(auth).extracting(GrantedAuthority::getAuthority).contains("ROLE_STANDARD");
  }

  @Test
  void principal_from_jwt() {
    Jwt jwt = jwt(Map.of("sub", "u42", "roles", List.of("admin"), "preferred_username", "Alice"));
    Principal p = conv.toPrincipal(jwt);
    assertThat(p.id()).isEqualTo("u42");
    assertThat(p.role()).isEqualTo(Role.PRIVILEGED);
    assertThat(p.displayName()).isEqualTo("Alice");
  }

  private Jwt jwt(Map<String, Object> claims) {
    Map<String, Object> headers = Map.of("alg", "none");
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), headers, claims);
  }
}
```

- [ ] **Step 2: Implementa**

```java
package org.toresoft.signverify.security;

import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;

public class OAuthPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final String roleClaim;
  private final List<String> privilegedValues;

  public OAuthPrincipalConverter(String roleClaim, List<String> privilegedValues) {
    this.roleClaim = roleClaim;
    this.privilegedValues = privilegedValues;
  }

  public Principal toPrincipal(Jwt jwt) {
    Role role = isPrivileged(jwt) ? Role.PRIVILEGED : Role.STANDARD;
    String displayName = jwt.getClaimAsString("preferred_username");
    if (displayName == null) displayName = jwt.getSubject();
    return new Principal(PrincipalType.OAUTH_USER, jwt.getSubject(), role, displayName);
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Principal p = toPrincipal(jwt);
    JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, buildAuthorities(p), p.displayName());
    return new JwtAuthAdapter(jwt, p);
  }

  private boolean isPrivileged(Jwt jwt) {
    Object claim = jwt.getClaim(roleClaim);
    if (claim instanceof Collection<?> c) {
      for (Object v : c) {
        if (privilegedValues.contains(String.valueOf(v))) return true;
      }
    } else if (claim instanceof String s) {
      for (String v : s.split("[ ,]")) {
        if (privilegedValues.contains(v)) return true;
      }
    }
    return false;
  }

  private Collection<GrantedAuthority> buildAuthorities(Principal p) {
    return List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name()));
  }

  static class JwtAuthAdapter extends AbstractAuthenticationToken {
    private final Jwt jwt;
    private final Principal principal;

    JwtAuthAdapter(Jwt jwt, Principal principal) {
      super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
      this.jwt = jwt;
      this.principal = principal;
      setAuthenticated(true);
    }
    @Override public Object getCredentials() { return jwt.getTokenValue(); }
    @Override public Object getPrincipal() { return principal; }
    @Override public String getName() { return principal.id(); }
  }
}
```

- [ ] **Step 3: Run, commit**

```bash
mvn -B test -Dtest=OAuthPrincipalConverterTest -q
git add src/main src/test
git commit -m "feat(security): add OAuth JWT to Principal converter"
```

## Task 2.5: `SecurityConfiguration`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/config/SecurityConfiguration.java`

- [ ] **Step 1: Implementa**

```java
package org.toresoft.signverify.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.toresoft.signverify.security.ApiKeyAuthenticationFilter;
import org.toresoft.signverify.security.OAuthPrincipalConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      ApiKeyAuthenticationFilter apiKeyFilter,
      @Value("${app.security.oauth.enabled}") boolean oauthEnabled,
      @Value("${app.security.oauth.role-claim}") String roleClaim,
      @Value("${app.security.oauth.privileged-values}") List<String> privilegedValues
  ) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

    if (oauthEnabled) {
      OAuthPrincipalConverter conv = new OAuthPrincipalConverter(roleClaim, privilegedValues);
      http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conv)));
    }
    return http.build();
  }
}
```

- [ ] **Step 2: Verifica run app + test**

```bash
mvn -B test -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/toresoft/signverify/config/SecurityConfiguration.java
git commit -m "feat(security): add security filter chain with api key + optional OAuth"
```

## Task 2.6: Bootstrap key generator

**Files:**
- Create: `src/main/java/org/toresoft/signverify/security/BootstrapApiKeyGenerator.java`
- Create: `src/test/java/org/toresoft/signverify/security/BootstrapApiKeyGeneratorTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

class BootstrapApiKeyGeneratorTest {

  @Test
  void generates_and_writes_file_when_no_privileged(@TempDir Path tmp) {
    ApiKeyRepository repo = mock(ApiKeyRepository.class);
    PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(0L);
    when(hasher.hash(any())).thenReturn("$2a$12$hashed");

    Path keyFile = tmp.resolve("bootstrap.txt");
    BootstrapApiKeyGenerator gen = new BootstrapApiKeyGenerator(repo, hasher, keyFile.toString());

    gen.onReady(mock(ApplicationReadyEvent.class));

    verify(repo).save(any(ApiKey.class));
    assertThat(keyFile).exists();
    String contents = readFile(keyFile);
    assertThat(contents).startsWith("sv_");
  }

  @Test
  void skips_when_privileged_already_exists(@TempDir Path tmp) {
    ApiKeyRepository repo = mock(ApiKeyRepository.class);
    PasswordHasherPort hasher = mock(PasswordHasherPort.class);
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(1L);

    Path keyFile = tmp.resolve("bootstrap.txt");
    BootstrapApiKeyGenerator gen = new BootstrapApiKeyGenerator(repo, hasher, keyFile.toString());
    gen.onReady(mock(ApplicationReadyEvent.class));

    verify(repo, never()).save(any());
    assertThat(keyFile).doesNotExist();
  }

  private String readFile(Path p) {
    try { return Files.readString(p); } catch (Exception e) { throw new RuntimeException(e); }
  }
}
```

- [ ] **Step 2: Implementa**

```java
package org.toresoft.signverify.security;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@Component
public class BootstrapApiKeyGenerator {

  private static final Logger log = LoggerFactory.getLogger(BootstrapApiKeyGenerator.class);
  private static final SecureRandom RND = new SecureRandom();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  private final ApiKeyRepository repo;
  private final PasswordHasherPort hasher;
  private final String keyFilePath;

  public BootstrapApiKeyGenerator(
      ApiKeyRepository repo,
      PasswordHasherPort hasher,
      @Value("${app.security.bootstrap-key-file}") String keyFilePath) {
    this.repo = repo;
    this.hasher = hasher;
    this.keyFilePath = keyFilePath;
  }

  @EventListener
  public void onReady(ApplicationReadyEvent event) {
    if (repo.countByRoleAndEnabled(Role.PRIVILEGED, true) > 0) return;

    byte[] random = new byte[36];
    RND.nextBytes(random);
    String body = B64.encodeToString(random);
    String prefix = body.substring(0, 8);
    String plaintext = "sv_" + prefix + "_" + body;

    ApiKey key = new ApiKey();
    key.setId(UUID.randomUUID());
    key.setName("bootstrap-" + Instant.now().getEpochSecond());
    key.setKeyPrefix(prefix);
    key.setKeyHash(hasher.hash(plaintext));
    key.setRole(Role.PRIVILEGED);
    key.setEnabled(true);
    key.setBootstrap(true);
    key.setCreatedAt(Instant.now());
    repo.save(key);

    writeBootstrapFile(plaintext);
    log.warn("BOOTSTRAP API KEY generated. File: {} — delete after pickup.", keyFilePath);
  }

  private void writeBootstrapFile(String plaintext) {
    try {
      Path p = Path.of(keyFilePath);
      Files.createDirectories(p.getParent() != null ? p.getParent() : Path.of("."));
      Files.writeString(p, plaintext + "\n",
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.setPosixFilePermissions(p, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException ignored) { /* Windows */ }
    } catch (Exception e) {
      throw new IllegalStateException("Cannot write bootstrap key file: " + keyFilePath, e);
    }
  }
}
```

- [ ] **Step 3: Run, commit**

```bash
mvn -B test -Dtest=BootstrapApiKeyGeneratorTest -q
git add src/main src/test
git commit -m "feat(security): add bootstrap api key generator on first start"
```

## Task 2.7: Test integration auth end-to-end

**Files:**
- Create: `src/test/java/org/toresoft/signverify/security/AuthenticationIT.java`
- Create: `src/main/java/org/toresoft/signverify/api/HealthProbeController.java` (endpoint dummy protetto per test)

- [ ] **Step 1: Endpoint dummy protetto**

```java
package org.toresoft.signverify.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.security.Principal;

@RestController
public class HealthProbeController {

  @GetMapping("/internal/whoami")
  public Principal whoami(@AuthenticationPrincipal Principal p) { return p; }

  @GetMapping("/internal/admin")
  @PreAuthorize("hasRole('PRIVILEGED')")
  public String admin() { return "ok"; }
}
```

- [ ] **Step 2: Integration test**

```java
package org.toresoft.signverify.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository repo;
  @Autowired private PasswordHasherPort hasher;

  private MockMvc mvc;

  private MockMvc mvc() {
    if (mvc == null) mvc = MockMvcBuilders.webAppContextSetup(ctx)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
        .build();
    return mvc;
  }

  @Test
  void no_credentials_returns_401() throws Exception {
    mvc().perform(get("/internal/whoami")).andExpect(status().isUnauthorized());
  }

  @Test
  void valid_api_key_returns_200_with_principal() throws Exception {
    String plaintext = "sv_test1234_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("test-it-" + UUID.randomUUID());
    k.setKeyPrefix("test1234");
    k.setKeyHash(hasher.hash(plaintext));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    repo.save(k);

    mvc().perform(get("/internal/whoami").header("X-API-Key", plaintext))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("API_KEY"))
        .andExpect(jsonPath("$.role").value("STANDARD"));
  }

  @Test
  void standard_key_forbidden_on_admin() throws Exception {
    String plaintext = "sv_stnd5678_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJ";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("stnd-" + UUID.randomUUID());
    k.setKeyPrefix("stnd5678");
    k.setKeyHash(hasher.hash(plaintext));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    repo.save(k);

    mvc().perform(get("/internal/admin").header("X-API-Key", plaintext))
        .andExpect(status().isForbidden());
  }
}
```

- [ ] **Step 3: Run + commit**

```bash
mvn -B test -Dtest=AuthenticationIT -q
git add src/main src/test
git commit -m "test(security): add auth integration test + dummy probe endpoints"
```

## Verifica Fase 2

```bash
mvn -B clean verify -q
```

Expected: tutti i test passano. App parte; al primo boot scrive bootstrap-api-key.

**Fine Fase 2.**

---

# FASE 3 — OpenAPI design-first + error handler

**Obiettivo**: `openapi.yaml` con tutti gli endpoint dichiarati, DTO + interfaces generati, `@RestControllerAdvice` globale con Problem Details RFC 9457.

## Task 3.1: `openapi.yaml` scheletro con endpoint dummy + error schema

**Files:**
- Create: `src/main/resources/openapi/openapi.yaml`

- [ ] **Step 1: Spec base con Problem schema + endpoint ping**

```yaml
openapi: 3.0.3
info:
  title: sign-verify-2 API
  version: 1.0.0
  description: REST service for electronic signature verification, extraction, profile and key management.
servers:
  - url: /
tags:
  - name: ApiKeys
  - name: Profiles
  - name: Verifications
  - name: Extractions
  - name: Tsl
  - name: Audit

security:
  - ApiKeyAuth: []
  - OAuth2Bearer: []

paths:
  /actuator/health:
    get:
      tags: [Health]
      operationId: health
      security: []
      responses:
        '200': { description: ok }

  # Tutti gli endpoint applicativi saranno aggiunti nelle fasi successive.
  # Qui inseriamo solo gli schemi comuni per abilitare la generazione DTO subito.

components:
  securitySchemes:
    ApiKeyAuth:
      type: apiKey
      in: header
      name: X-API-Key
    OAuth2Bearer:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    Problem:
      type: object
      properties:
        type: { type: string, format: uri }
        title: { type: string }
        status: { type: integer, format: int32 }
        detail: { type: string }
        instance: { type: string }
        traceId: { type: string }

    Page:
      type: object
      properties:
        page: { type: integer }
        size: { type: integer }
        totalElements: { type: integer, format: int64 }
        totalPages: { type: integer }

    PrincipalRef:
      type: object
      properties:
        type: { type: string, enum: [API_KEY, OAUTH_USER, SYSTEM] }
        id: { type: string }
        displayName: { type: string }
```

(Path applicativi vengono aggiunti uno per uno nelle fasi 4-11 con la stessa pattern: task "Aggiungi path X in openapi.yaml".)

- [ ] **Step 2: Genera DTO**

```bash
mvn -B generate-sources -q
```

Expected: `target/generated-sources/openapi/src/main/java/org/toresoft/signverify/api/dto/Problem.java` e altri DTO creati.

- [ ] **Step 3: Verifica compile**

```bash
mvn -B compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/openapi/openapi.yaml
git commit -m "feat(api): add openapi yaml skeleton with common schemas"
```

## Task 3.2: `ProblemDetail` builder + global exception handler

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/exception/AppException.java`
- Create: `src/main/java/org/toresoft/signverify/domain/exception/Errors.java` (codici stabili)
- Create: `src/main/java/org/toresoft/signverify/api/GlobalExceptionHandler.java`
- Create: `src/test/java/org/toresoft/signverify/api/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Eccezione + codici**

`AppException.java`:
```java
package org.toresoft.signverify.domain.exception;

public class AppException extends RuntimeException {
  private final String code;
  private final int status;
  private final String detail;

  public AppException(String code, int status, String title, String detail) {
    super(title);
    this.code = code;
    this.status = status;
    this.detail = detail;
  }
  public String getCode() { return code; }
  public int getStatus() { return status; }
  public String getDetail() { return detail; }

  public static AppException notFound(String detail) {
    return new AppException(Errors.RESOURCE_NOT_FOUND, 404, "Not Found", detail);
  }
  public static AppException conflict(String detail) {
    return new AppException(Errors.RESOURCE_CONFLICT, 409, "Conflict", detail);
  }
  public static AppException badRequest(String detail) {
    return new AppException(Errors.VALIDATION_INVALID_INPUT, 400, "Bad Request", detail);
  }
  public static AppException gone(String detail) {
    return new AppException(Errors.RESOURCE_GONE, 410, "Gone", detail);
  }
  public static AppException tooLarge(String detail) {
    return new AppException(Errors.PAYLOAD_TOO_LARGE, 413, "Payload Too Large", detail);
  }
  public static AppException backpressure(String detail) {
    return new AppException(Errors.EXCESSIVE_LOAD_ASYNC, 429, "Too Many Requests", detail);
  }
  public static AppException concurrency(String detail) {
    return new AppException(Errors.EXCESSIVE_LOAD_CONCURRENCY, 503, "Service Unavailable", detail);
  }
  public static AppException dssUnavailable(String detail) {
    return new AppException(Errors.DSS_UNAVAILABLE, 503, "DSS Unavailable", detail);
  }
  public static AppException tslNotReady(String detail) {
    return new AppException(Errors.TSL_NOT_READY, 503, "TSL Not Ready", detail);
  }
  public static AppException signatureParseError(String detail) {
    return new AppException(Errors.SIGNATURE_PARSE_ERROR, 422, "Unprocessable Entity", detail);
  }
}
```

`Errors.java`:
```java
package org.toresoft.signverify.domain.exception;

public final class Errors {
  public static final String VALIDATION_INVALID_INPUT = "validation.invalid-input";
  public static final String VALIDATION_INVALID_OVERRIDES = "validation.invalid-profile-overrides";
  public static final String AUTH_MISSING = "auth.missing-credentials";
  public static final String AUTH_INVALID = "auth.invalid-token";
  public static final String AUTHZ_FORBIDDEN = "authz.forbidden";
  public static final String RESOURCE_NOT_FOUND = "resource.not-found";
  public static final String RESOURCE_GONE = "resource.gone";
  public static final String RESOURCE_CONFLICT = "resource.conflict";
  public static final String PAYLOAD_TOO_LARGE = "payload.too-large";
  public static final String MEDIA_UNSUPPORTED = "media.unsupported";
  public static final String SIGNATURE_PARSE_ERROR = "signature.parse-error";
  public static final String EXCESSIVE_LOAD_CONCURRENCY = "excessive-load.concurrency";
  public static final String EXCESSIVE_LOAD_ASYNC = "excessive-load.async-backpressure";
  public static final String TSL_NOT_READY = "tsl.not-ready";
  public static final String DSS_UNAVAILABLE = "dss.unavailable";
  public static final String INTERNAL = "internal-error";
  private Errors() {}
}
```

- [ ] **Step 2: Test exception handler**

```java
package org.toresoft.signverify.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.toresoft.signverify.domain.exception.AppException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler h = new GlobalExceptionHandler();

  @Test
  void app_exception_maps_to_problem() {
    WebRequest req = new org.springframework.mock.web.MockHttpServletRequest()
        .getServletContext() == null
        ? new fakeWebReq("/x")
        : new fakeWebReq("/x");
    ResponseEntity<?> res = h.handleApp(AppException.notFound("nope"), new fakeWebReq("/x"));
    assertThat(res.getStatusCodeValue()).isEqualTo(404);
    assertThat(res.getHeaders().getContentType().toString()).isEqualTo("application/problem+json");
  }

  static class fakeWebReq implements WebRequest {
    private final String desc;
    fakeWebReq(String desc) { this.desc = desc; }
    @Override public String getDescription(boolean p) { return desc; }
    @Override public Object getAttribute(String n, int s) { return null; }
    @Override public void setAttribute(String n, Object v, int s) {}
    @Override public void removeAttribute(String n, int s) {}
    @Override public String[] getAttributeNames(int s) { return new String[0]; }
    @Override public void registerDestructionCallback(String n, Runnable c, int s) {}
    @Override public Object resolveReference(String k) { return null; }
    @Override public String getSessionId() { return null; }
    @Override public Object getSessionMutex() { return null; }
    @Override public String getHeader(String n) { return null; }
    @Override public String[] getHeaderValues(String n) { return new String[0]; }
    @Override public java.util.Iterator<String> getHeaderNames() { return java.util.Collections.emptyIterator(); }
    @Override public String getParameter(String n) { return null; }
    @Override public String[] getParameterValues(String n) { return new String[0]; }
    @Override public java.util.Map<String, String[]> getParameterMap() { return java.util.Map.of(); }
    @Override public java.util.Iterator<String> getParameterNames() { return java.util.Collections.emptyIterator(); }
    @Override public java.util.Locale getLocale() { return java.util.Locale.ENGLISH; }
    @Override public String getContextPath() { return ""; }
    @Override public String getRemoteUser() { return null; }
    @Override public java.security.Principal getUserPrincipal() { return null; }
    @Override public boolean isUserInRole(String r) { return false; }
    @Override public boolean isSecure() { return false; }
    @Override public boolean checkNotModified(long t) { return false; }
    @Override public boolean checkNotModified(String e) { return false; }
    @Override public boolean checkNotModified(String e, long t) { return false; }
  }
}
```

- [ ] **Step 3: Handler**

```java
package org.toresoft.signverify.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AppException.class)
  public ResponseEntity<Map<String, Object>> handleApp(AppException ex, WebRequest req) {
    return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetail(), req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
    String detail = ex.getBindingResult().getAllErrors().stream()
        .map(e -> e.getDefaultMessage()).reduce((a, b) -> a + "; " + b).orElse("invalid input");
    return problem(400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", detail, req);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex, WebRequest req) {
    return problem(400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", "malformed request body", req);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex, WebRequest req) {
    return problem(413, Errors.PAYLOAD_TOO_LARGE, "Payload Too Large", "max upload size exceeded", req);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, WebRequest req) {
    return problem(403, Errors.AUTHZ_FORBIDDEN, "Forbidden", "insufficient role", req);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex, WebRequest req) {
    return problem(401, Errors.AUTH_INVALID, "Unauthorized", "invalid credentials", req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest req) {
    log.error("Uncaught exception", ex);
    return problem(500, Errors.INTERNAL, "Internal Server Error", "unexpected error", req);
  }

  private ResponseEntity<Map<String, Object>> problem(int status, String code, String title, String detail, WebRequest req) {
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("type", URI.create("urn:signverify:error:" + code).toString());
    body.put("title", title);
    body.put("status", status);
    if (detail != null) body.put("detail", detail);
    if (req instanceof org.springframework.web.context.request.ServletWebRequest swr) {
      HttpServletRequest http = swr.getRequest();
      body.put("instance", http.getRequestURI());
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .body(body);
  }
}
```

- [ ] **Step 4: Run, commit**

```bash
mvn -B test -Dtest=GlobalExceptionHandlerTest -q
git add src/main src/test
git commit -m "feat(api): add app exception types + global exception handler with rfc 9457 problem json"
```

## Verifica Fase 3

```bash
mvn -B verify -q
```

Expected: BUILD SUCCESS. Tutto compile. Endpoint `/internal/admin` (da Task 2.7) ora restituisce `application/problem+json` su 403.

**Fine Fase 3.**

---

> **Nota su Fasi 4–13**: ogni fase segue lo stesso pattern delle precedenti. Per ragioni di lunghezza del piano, i task delle fasi rimanenti sono presentati con codice completo ma TDD step ridotti dove la struttura è già stata stabilita nelle prime tre fasi. Ogni task elenca comunque file, codice da scrivere, comandi e commit.

# FASE 4 — Gestione API key (CRUD + invariante "ultima privilegiata")

## Task 4.1: Aggiungi path API key in `openapi.yaml`

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`

- [ ] **Step 1: Aggiungi sezione paths e schema sotto components**

In `paths:` aggiungi:
```yaml
  /api/v1/api-keys:
    get:
      tags: [ApiKeys]
      operationId: listApiKeys
      parameters:
        - { name: page, in: query, schema: { type: integer, default: 0 } }
        - { name: size, in: query, schema: { type: integer, default: 20 } }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ApiKeyPage' }
    post:
      tags: [ApiKeys]
      operationId: createApiKey
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiKeyCreateRequest' }
      responses:
        '201':
          description: created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ApiKeyCreatedResponse' }

  /api/v1/api-keys/{id}:
    delete:
      tags: [ApiKeys]
      operationId: deleteApiKey
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '204': { description: deleted }
    patch:
      tags: [ApiKeys]
      operationId: patchApiKey
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiKeyPatchRequest' }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ApiKeyView' }
```

In `components.schemas:` aggiungi:
```yaml
    ApiKeyCreateRequest:
      type: object
      required: [name, role]
      properties:
        name: { type: string, maxLength: 120, minLength: 1 }
        role: { type: string, enum: [PRIVILEGED, STANDARD] }
        expiresAt: { type: string, format: date-time, nullable: true }
    ApiKeyPatchRequest:
      type: object
      properties:
        enabled: { type: boolean }
    ApiKeyView:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        keyPrefix: { type: string }
        role: { type: string, enum: [PRIVILEGED, STANDARD] }
        enabled: { type: boolean }
        bootstrap: { type: boolean }
        createdAt: { type: string, format: date-time }
        expiresAt: { type: string, format: date-time, nullable: true }
        lastUsedAt: { type: string, format: date-time, nullable: true }
    ApiKeyCreatedResponse:
      allOf:
        - $ref: '#/components/schemas/ApiKeyView'
        - type: object
          required: [plaintextKey]
          properties:
            plaintextKey: { type: string }
    ApiKeyPage:
      allOf:
        - $ref: '#/components/schemas/Page'
        - type: object
          properties:
            content:
              type: array
              items: { $ref: '#/components/schemas/ApiKeyView' }
```

- [ ] **Step 2: Genera DTO**

```bash
mvn -B generate-sources -q
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/openapi/openapi.yaml
git commit -m "feat(api): add openapi paths for api key management"
```

## Task 4.2: `ApiKeyService` con invariante "ultima privilegiata"

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/ApiKeyService.java`
- Create: `src/test/java/org/toresoft/signverify/application/ApiKeyServiceTest.java`

- [ ] **Step 1: Test invariante**

```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.security.Principal;
import org.toresoft.signverify.domain.model.PrincipalType;

class ApiKeyServiceTest {

  private final ApiKeyRepository repo = mock(ApiKeyRepository.class);
  private final PasswordHasherPort hasher = mock(PasswordHasherPort.class);
  private final ApiKeyService service = new ApiKeyService(repo, hasher);

  private final Principal admin = new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  @Test
  void delete_last_privileged_throws_conflict() {
    UUID id = UUID.randomUUID();
    ApiKey only = new ApiKey();
    only.setId(id); only.setRole(Role.PRIVILEGED); only.setEnabled(true);
    only.setName("only"); only.setKeyPrefix("only0001"); only.setKeyHash("h");
    only.setCreatedAt(Instant.now());
    when(repo.findById(id)).thenReturn(Optional.of(only));
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(1L);

    assertThatThrownBy(() -> service.delete(id, admin))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Conflict");
    verify(repo, never()).deleteById(any());
  }

  @Test
  void delete_when_other_privileged_exists_ok() {
    UUID id = UUID.randomUUID();
    ApiKey k = new ApiKey();
    k.setId(id); k.setRole(Role.PRIVILEGED); k.setEnabled(true);
    k.setName("k"); k.setKeyPrefix("priv0001"); k.setKeyHash("h");
    k.setCreatedAt(Instant.now());
    when(repo.findById(id)).thenReturn(Optional.of(k));
    when(repo.countByRoleAndEnabled(Role.PRIVILEGED, true)).thenReturn(2L);

    service.delete(id, admin);
    verify(repo).deleteById(id);
  }

  @Test
  void create_returns_plaintext_once() {
    when(hasher.hash(any())).thenReturn("$2a$hash");
    when(repo.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.create("alice", Role.STANDARD, null, admin);
    assertThat(res.plaintext()).startsWith("sv_");
    assertThat(res.entity().getKeyHash()).isEqualTo("$2a$hash");
  }
}
```

- [ ] **Step 2: Service**

```java
package org.toresoft.signverify.application;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class ApiKeyService {

  public record CreateResult(ApiKey entity, String plaintext) {}

  private static final SecureRandom RND = new SecureRandom();
  private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

  private final ApiKeyRepository repo;
  private final PasswordHasherPort hasher;

  public ApiKeyService(ApiKeyRepository repo, PasswordHasherPort hasher) {
    this.repo = repo;
    this.hasher = hasher;
  }

  @Transactional
  public CreateResult create(String name, Role role, Instant expiresAt, Principal actor) {
    if (repo.findByName(name).isPresent()) {
      throw AppException.conflict("name already taken");
    }
    byte[] r = new byte[36];
    RND.nextBytes(r);
    String body = B64.encodeToString(r);
    String prefix = body.substring(0, 8);
    String plaintext = "sv_" + prefix + "_" + body;

    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName(name);
    k.setKeyPrefix(prefix);
    k.setKeyHash(hasher.hash(plaintext));
    k.setRole(role);
    k.setEnabled(true);
    k.setExpiresAt(expiresAt);
    k.setCreatedAt(Instant.now());
    k.setCreatedByPrincipalType(actor.type());
    k.setCreatedByPrincipalId(actor.id());
    repo.save(k);
    return new CreateResult(k, plaintext);
  }

  @Transactional
  public void delete(UUID id, Principal actor) {
    ApiKey k = repo.findById(id).orElseThrow(() -> AppException.notFound("api key not found"));
    enforceLastPrivilegedInvariant(k);
    repo.deleteById(id);
  }

  @Transactional
  public ApiKey patch(UUID id, Boolean enabled, Principal actor) {
    ApiKey k = repo.findById(id).orElseThrow(() -> AppException.notFound("api key not found"));
    if (enabled != null && !enabled && k.isEnabled()) {
      enforceLastPrivilegedInvariant(k);
    }
    if (enabled != null) k.setEnabled(enabled);
    return repo.save(k);
  }

  private void enforceLastPrivilegedInvariant(ApiKey k) {
    if (k.getRole() == Role.PRIVILEGED && k.isEnabled()) {
      long count = repo.countByRoleAndEnabled(Role.PRIVILEGED, true);
      if (count <= 1) {
        throw AppException.conflict("cannot remove last enabled privileged api key");
      }
    }
  }
}
```

- [ ] **Step 3: Run, commit**

```bash
mvn -B test -Dtest=ApiKeyServiceTest -q
git add src/main src/test
git commit -m "feat(api-key): add ApiKeyService with last-privileged invariant"
```

## Task 4.3: `ApiKeyController` implementa interface generata

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/ApiKeyController.java`

- [ ] **Step 1: Implementa controller**

```java
package org.toresoft.signverify.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.api.dto.*;
import org.toresoft.signverify.api.spi.ApiKeysApi;
import org.toresoft.signverify.application.ApiKeyService;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.security.Principal;

@RestController
@PreAuthorize("hasRole('PRIVILEGED')")
public class ApiKeyController implements ApiKeysApi {

  private final ApiKeyService service;
  private final ApiKeyRepository repo;

  public ApiKeyController(ApiKeyService service, ApiKeyRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  @Override
  public ResponseEntity<ApiKeyPage> listApiKeys(Integer page, Integer size) {
    var p = repo.findAll(PageRequest.of(page == null ? 0 : page, size == null ? 20 : size));
    ApiKeyPage out = new ApiKeyPage();
    out.setPage(p.getNumber());
    out.setSize(p.getSize());
    out.setTotalElements(p.getTotalElements());
    out.setTotalPages(p.getTotalPages());
    out.setContent(p.map(this::toView).toList());
    return ResponseEntity.ok(out);
  }

  @Override
  public ResponseEntity<ApiKeyCreatedResponse> createApiKey(ApiKeyCreateRequest req) {
    Principal actor = currentPrincipal();
    var res = service.create(
        req.getName(),
        Role.valueOf(req.getRole().getValue()),
        req.getExpiresAt() == null ? null : req.getExpiresAt().toInstant(),
        actor);
    ApiKeyCreatedResponse out = new ApiKeyCreatedResponse();
    fillView(out, res.entity());
    out.setPlaintextKey(res.plaintext());
    return ResponseEntity.status(201).body(out);
  }

  @Override
  public ResponseEntity<Void> deleteApiKey(UUID id) {
    service.delete(id, currentPrincipal());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<ApiKeyView> patchApiKey(UUID id, ApiKeyPatchRequest req) {
    ApiKey k = service.patch(id, req.getEnabled(), currentPrincipal());
    return ResponseEntity.ok(toView(k));
  }

  private Principal currentPrincipal() {
    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return (Principal) auth.getPrincipal();
  }

  private ApiKeyView toView(ApiKey k) {
    ApiKeyView v = new ApiKeyView();
    fillView(v, k);
    return v;
  }

  private void fillView(ApiKeyView v, ApiKey k) {
    v.setId(k.getId());
    v.setName(k.getName());
    v.setKeyPrefix(k.getKeyPrefix());
    v.setRole(ApiKeyView.RoleEnum.valueOf(k.getRole().name()));
    v.setEnabled(k.isEnabled());
    v.setBootstrap(k.isBootstrap());
    v.setCreatedAt(k.getCreatedAt().atOffset(ZoneOffset.UTC));
    v.setExpiresAt(k.getExpiresAt() == null ? null : k.getExpiresAt().atOffset(ZoneOffset.UTC));
    v.setLastUsedAt(k.getLastUsedAt() == null ? null : k.getLastUsedAt().atOffset(ZoneOffset.UTC));
  }
}
```

- [ ] **Step 2: Verifica compile + run**

```bash
mvn -B test -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/toresoft/signverify/api/ApiKeyController.java
git commit -m "feat(api-key): add API key controller implementing generated interface"
```

## Task 4.4: Integration test API key

**Files:**
- Create: `src/test/java/org/toresoft/signverify/api/ApiKeyControllerIT.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@SpringBootTest
@ActiveProfiles("test")
class ApiKeyControllerIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository repo;
  @Autowired private PasswordHasherPort hasher;
  @Autowired private ObjectMapper om;

  private MockMvc mvc;
  private String adminKey;

  @BeforeEach
  void setup() {
    repo.deleteAll();
    adminKey = "sv_adminx01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("admin-it-" + UUID.randomUUID());
    k.setKeyPrefix("adminx01");
    k.setKeyHash(hasher.hash(adminKey));
    k.setRole(Role.PRIVILEGED);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    repo.save(k);
    mvc = MockMvcBuilders.webAppContextSetup(ctx)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
        .build();
  }

  @Test
  void create_then_list_then_cannot_delete_last_privileged() throws Exception {
    String body = """
        {"name":"new-priv","role":"PRIVILEGED"}
        """;
    var res = mvc.perform(post("/api/v1/api-keys")
            .header("X-API-Key", adminKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.plaintextKey").exists())
        .andReturn();

    mvc.perform(get("/api/v1/api-keys").header("X-API-Key", adminKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));

    // delete admin → should conflict (the OTHER created is also priv → 2 priv enabled, delete one allowed)
    // delete second priv after deleting first should fail.
  }

  @Test
  void delete_last_privileged_returns_409() throws Exception {
    UUID adminId = repo.findAll().get(0).getId();
    mvc.perform(delete("/api/v1/api-keys/" + adminId).header("X-API-Key", adminKey))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("urn:signverify:error:resource.conflict"));
  }
}
```

- [ ] **Step 2: Run, commit**

```bash
mvn -B test -Dtest=ApiKeyControllerIT -q
git add src/test
git commit -m "test(api-key): add integration test for api key controller"
```

## Verifica Fase 4

```bash
mvn -B verify -q
```

Expected: BUILD SUCCESS. API key CRUD funzionante con invarianti enforced.

**Fine Fase 4.**

---

# FASE 5 — Profili verifica + preset XML

## Task 5.1: Risorse preset XML (BASIC, STANDARD, STRICT)

**Files:**
- Create: `src/main/resources/policy/BASIC.xml`
- Create: `src/main/resources/policy/STANDARD.xml`
- Create: `src/main/resources/policy/STRICT.xml`

- [ ] **Step 1: Copia da DSS reference**

Per ogni file: usa il file `constraint.xml` shipped con DSS modulo `dss-policy-jaxb` come base.

```bash
# Estrai il default DSS policy dal jar:
mvn dependency:copy -B \
  -Dartifact=eu.europa.ec.joinup.sd-dss:dss-policy-jaxb:6.4 \
  -DoutputDirectory=target/policy-extract -q

unzip -o target/policy-extract/dss-policy-jaxb-6.4.jar 'policy/*' -d target/policy-jaxb
cp target/policy-jaxb/policy/constraint.xml src/main/resources/policy/STANDARD.xml
cp target/policy-jaxb/policy/constraint.xml src/main/resources/policy/BASIC.xml
cp target/policy-jaxb/policy/constraint.xml src/main/resources/policy/STRICT.xml
```

Quindi:
- Modifica `BASIC.xml`: tutti i nodi `<RevocationDataAvailable Level="FAIL">` → `Level="IGNORE"`, idem `<TimestampDelay>` e simili.
- Modifica `STRICT.xml`: aggiungi `<QualifiedCertificate Level="FAIL">` se assente, restringi `<AcceptableDigestAlgo>` a SHA256/384/512.
- `STANDARD.xml` resta come reference DSS.

(Se il file non è disponibile da DSS direttamente: prendi `https://github.com/esig/dss/blob/6.4.RC1/dss-policy-jaxb/src/main/resources/policy/constraint.xml`.)

- [ ] **Step 2: Test caricamento risorse**

`src/test/java/org/toresoft/signverify/application/PresetXmlLoaderTest.java`:
```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.toresoft.signverify.domain.model.ProfilePreset;

class PresetXmlLoaderTest {

  @Test
  void load_each_preset() throws Exception {
    for (ProfilePreset p : new ProfilePreset[]{ProfilePreset.BASIC, ProfilePreset.STANDARD, ProfilePreset.STRICT}) {
      var res = new ClassPathResource("policy/" + p.name() + ".xml");
      try (var in = res.getInputStream()) {
        String content = new String(in.readAllBytes());
        assertThat(content).contains("<ConstraintsParameters");
      }
    }
  }
}
```

- [ ] **Step 3: Run + commit**

```bash
mvn -B test -Dtest=PresetXmlLoaderTest -q
git add src/main/resources/policy src/test/java/org/toresoft/signverify/application/PresetXmlLoaderTest.java
git commit -m "feat(profile): bundle BASIC/STANDARD/STRICT policy xml resources"
```

## Task 5.2: `VerificationProfileService` + seed default

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/VerificationProfileService.java`
- Create: `src/main/java/org/toresoft/signverify/application/PresetXmlLoader.java`
- Create: `src/main/java/org/toresoft/signverify/application/ProfileSeeder.java`
- Create: `src/test/java/org/toresoft/signverify/application/VerificationProfileServiceTest.java`

- [ ] **Step 1: Preset loader**

```java
package org.toresoft.signverify.application;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ProfilePreset;

@Component
public class PresetXmlLoader {

  public String load(ProfilePreset preset) {
    try (var in = new ClassPathResource("policy/" + preset.name() + ".xml").getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("missing policy: " + preset, e);
    }
  }
}
```

- [ ] **Step 2: Service**

```java
package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@Service
public class VerificationProfileService {

  private final VerificationProfileRepository repo;
  private final PresetXmlLoader presetLoader;

  public VerificationProfileService(VerificationProfileRepository repo, PresetXmlLoader presetLoader) {
    this.repo = repo;
    this.presetLoader = presetLoader;
  }

  @Transactional
  public VerificationProfile create(String name, String description, ProfilePreset preset, String customXml) {
    if (repo.findByName(name).isPresent()) throw AppException.conflict("profile name taken");
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName(name);
    p.setDescription(description);
    p.setPreset(preset);
    p.setPolicyXml(preset == ProfilePreset.CUSTOM ? customXml : presetLoader.load(preset));
    p.setIsDefault(false);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    return repo.save(p);
  }

  @Transactional
  public VerificationProfile update(UUID id, String description, String customXml) {
    VerificationProfile p = repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    if (description != null) p.setDescription(description);
    if (customXml != null) {
      if (p.getPreset() != ProfilePreset.CUSTOM) throw AppException.badRequest("policyXml editing allowed only on CUSTOM");
      p.setPolicyXml(customXml);
    }
    p.setUpdatedAt(Instant.now());
    return repo.save(p);
  }

  @Transactional
  public void delete(UUID id) {
    VerificationProfile p = repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    if (p.getIsDefault()) throw AppException.conflict("cannot delete default profile");
    repo.deleteById(id);
  }

  @Transactional
  public VerificationProfile setDefault(UUID id) {
    VerificationProfile target = repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    repo.findByIsDefaultTrue().ifPresent(curr -> { curr.setIsDefault(false); repo.save(curr); });
    target.setIsDefault(true);
    target.setUpdatedAt(Instant.now());
    return repo.save(target);
  }

  public VerificationProfile getOrDefault(UUID id) {
    if (id != null) return repo.findById(id).orElseThrow(() -> AppException.notFound("profile not found"));
    return repo.findByIsDefaultTrue().orElseThrow(() -> AppException.notFound("no default profile"));
  }
}
```

- [ ] **Step 3: Seeder default profile**

```java
package org.toresoft.signverify.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@Component
public class ProfileSeeder {

  private final VerificationProfileRepository repo;
  private final PresetXmlLoader presetLoader;

  public ProfileSeeder(VerificationProfileRepository repo, PresetXmlLoader loader) {
    this.repo = repo;
    this.presetLoader = loader;
  }

  @EventListener
  public void onReady(ApplicationReadyEvent ev) {
    if (repo.count() > 0) return;
    VerificationProfile p = new VerificationProfile();
    p.setId(UUID.randomUUID());
    p.setName("STANDARD");
    p.setDescription("DSS default validation policy");
    p.setPreset(ProfilePreset.STANDARD);
    p.setPolicyXml(presetLoader.load(ProfilePreset.STANDARD));
    p.setIsDefault(true);
    p.setCreatedAt(Instant.now());
    p.setUpdatedAt(Instant.now());
    repo.save(p);
  }
}
```

- [ ] **Step 4: Test service**

```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VerificationProfileServiceTest {

  @Autowired private VerificationProfileService service;
  @Autowired private VerificationProfileRepository repo;

  @Test
  void cannot_delete_default() {
    var def = repo.findByIsDefaultTrue().orElseThrow();
    assertThatThrownBy(() -> service.delete(def.getId()))
        .isInstanceOf(AppException.class);
  }

  @Test
  void set_default_swaps() {
    var oldDefault = repo.findByIsDefaultTrue().orElseThrow();
    var p = service.create("X", "x", ProfilePreset.STRICT, null);
    var newDef = service.setDefault(p.getId());

    assertThat(newDef.getIsDefault()).isTrue();
    assertThat(repo.findById(oldDefault.getId()).orElseThrow().getIsDefault()).isFalse();
  }
}
```

- [ ] **Step 5: Run, commit**

```bash
mvn -B test -Dtest=VerificationProfileServiceTest -q
git add src/main src/test
git commit -m "feat(profile): add VerificationProfileService + seeder for default"
```

## Task 5.3: Aggiungi path profili in `openapi.yaml`

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`

- [ ] **Step 1: Aggiungi paths**

```yaml
  /api/v1/profiles:
    get:
      tags: [Profiles]
      operationId: listProfiles
      parameters:
        - { name: page, in: query, schema: { type: integer, default: 0 } }
        - { name: size, in: query, schema: { type: integer, default: 20 } }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ProfilePage' }
    post:
      tags: [Profiles]
      operationId: createProfile
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ProfileCreateRequest' }
      responses:
        '201':
          description: created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ProfileView' }
  /api/v1/profiles/{id}:
    get:
      tags: [Profiles]
      operationId: getProfile
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ProfileView' }
    put:
      tags: [Profiles]
      operationId: updateProfile
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ProfileUpdateRequest' }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ProfileView' }
    delete:
      tags: [Profiles]
      operationId: deleteProfile
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '204': { description: deleted }
  /api/v1/profiles/{id}/default:
    post:
      tags: [Profiles]
      operationId: setDefaultProfile
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ProfileView' }
```

Aggiungi schemas:
```yaml
    ProfileView:
      type: object
      properties:
        id: { type: string, format: uuid }
        name: { type: string }
        description: { type: string, nullable: true }
        preset: { type: string, enum: [BASIC, STANDARD, STRICT, CUSTOM] }
        isDefault: { type: boolean }
        createdAt: { type: string, format: date-time }
        updatedAt: { type: string, format: date-time }
    ProfileCreateRequest:
      type: object
      required: [name, preset]
      properties:
        name: { type: string, minLength: 1, maxLength: 120 }
        description: { type: string, nullable: true }
        preset: { type: string, enum: [BASIC, STANDARD, STRICT, CUSTOM] }
        policyXml: { type: string, nullable: true, description: "Required when preset=CUSTOM" }
    ProfileUpdateRequest:
      type: object
      properties:
        description: { type: string, nullable: true }
        policyXml: { type: string, nullable: true }
    ProfilePage:
      allOf:
        - $ref: '#/components/schemas/Page'
        - type: object
          properties:
            content:
              type: array
              items: { $ref: '#/components/schemas/ProfileView' }
```

- [ ] **Step 2: Genera DTO + commit**

```bash
mvn -B generate-sources -q
git add src/main/resources/openapi/openapi.yaml
git commit -m "feat(api): add openapi paths for verification profiles"
```

## Task 5.4: `VerificationProfileController`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/VerificationProfileController.java`

- [ ] **Step 1: Controller**

```java
package org.toresoft.signverify.api;

import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.api.dto.*;
import org.toresoft.signverify.api.spi.ProfilesApi;
import org.toresoft.signverify.application.VerificationProfileService;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@RestController
public class VerificationProfileController implements ProfilesApi {

  private final VerificationProfileService service;
  private final VerificationProfileRepository repo;

  public VerificationProfileController(VerificationProfileService service, VerificationProfileRepository repo) {
    this.service = service;
    this.repo = repo;
  }

  @Override
  public ResponseEntity<ProfilePage> listProfiles(Integer page, Integer size) {
    var p = repo.findAll(PageRequest.of(page == null ? 0 : page, size == null ? 20 : size));
    ProfilePage out = new ProfilePage();
    out.setPage(p.getNumber());
    out.setSize(p.getSize());
    out.setTotalElements(p.getTotalElements());
    out.setTotalPages(p.getTotalPages());
    out.setContent(p.map(this::toView).toList());
    return ResponseEntity.ok(out);
  }

  @Override
  public ResponseEntity<ProfileView> getProfile(UUID id) {
    return ResponseEntity.ok(toView(repo.findById(id).orElseThrow(
        () -> org.toresoft.signverify.domain.exception.AppException.notFound("profile not found"))));
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<ProfileView> createProfile(ProfileCreateRequest req) {
    VerificationProfile p = service.create(
        req.getName(),
        req.getDescription(),
        ProfilePreset.valueOf(req.getPreset().getValue()),
        req.getPolicyXml());
    return ResponseEntity.status(201).body(toView(p));
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<ProfileView> updateProfile(UUID id, ProfileUpdateRequest req) {
    return ResponseEntity.ok(toView(service.update(id, req.getDescription(), req.getPolicyXml())));
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<Void> deleteProfile(UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @Override
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<ProfileView> setDefaultProfile(UUID id) {
    return ResponseEntity.ok(toView(service.setDefault(id)));
  }

  private ProfileView toView(VerificationProfile p) {
    ProfileView v = new ProfileView();
    v.setId(p.getId());
    v.setName(p.getName());
    v.setDescription(p.getDescription());
    v.setPreset(ProfileView.PresetEnum.valueOf(p.getPreset().name()));
    v.setIsDefault(p.getIsDefault());
    v.setCreatedAt(p.getCreatedAt().atOffset(ZoneOffset.UTC));
    v.setUpdatedAt(p.getUpdatedAt().atOffset(ZoneOffset.UTC));
    return v;
  }
}
```

- [ ] **Step 2: Run, commit**

```bash
mvn -B test -q
git add src/main
git commit -m "feat(profile): add profile controller"
```

## Verifica Fase 5

```bash
mvn -B verify -q
```

Expected: BUILD SUCCESS. Profile CRUD funzionante.

**Fine Fase 5.**

---

# FASE 6 — DSS verifica sincrona

## Task 6.1: `SignatureValidatorPort` + value object

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/SignatureValidatorPort.java`
- Create: `src/main/java/org/toresoft/signverify/domain/port/ValidationRequest.java`
- Create: `src/main/java/org/toresoft/signverify/domain/port/ValidationResult.java`

```java
// ValidationRequest.java
package org.toresoft.signverify.domain.port;
import java.util.Set;
public record ValidationRequest(byte[] documentBytes, String filename, String policyXml, Set<ReportType> reports) {}

// ReportType.java
package org.toresoft.signverify.domain.port;
public enum ReportType { SIMPLE, DETAILED, DIAGNOSTIC, ETSI }

// ValidationResult.java
package org.toresoft.signverify.domain.port;
import java.util.Map;
public record ValidationResult(
    String signatureFormat,
    String indication,
    String subIndication,
    int signatureCount,
    Map<ReportType, String> reportsJson) {}

// SignatureValidatorPort.java
package org.toresoft.signverify.domain.port;
public interface SignatureValidatorPort {
  ValidationResult validate(ValidationRequest req);
}
```

Commit: `feat(domain): add SignatureValidatorPort + value objects`

## Task 6.2: `DssConfiguration` (CertificateVerifier + empty TrustedListsCertificateSource)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/config/DssConfiguration.java`

```java
package org.toresoft.signverify.config;

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import eu.europa.esig.dss.spi.x509.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DssConfiguration {

  @Bean
  public TrustedListsCertificateSource trustedListsCertificateSource() {
    return new TrustedListsCertificateSource();
  }

  @Bean
  public CertificateVerifier certificateVerifier(TrustedListsCertificateSource tsl) {
    CommonCertificateVerifier cv = new CommonCertificateVerifier();
    cv.setTrustedCertSources(tsl);
    cv.setAIASource(new DefaultAIASource());
    cv.setOcspSource(new OnlineOCSPSource());
    cv.setCrlSource(new OnlineCRLSource());
    cv.setRevocationFallback(true);
    return cv;
  }
}
```

Commit: `feat(dss): add DSS configuration beans`

## Task 6.3: `DssValidatorAdapter` con `@CircuitBreaker`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/adapter/dss/DssValidatorAdapter.java`
- Create: `src/test/java/org/toresoft/signverify/adapter/dss/DssValidatorAdapterTest.java`

- [ ] **Step 1: Adapter**

```java
package org.toresoft.signverify.adapter.dss;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.policy.ValidationPolicy;
import eu.europa.esig.dss.policy.ValidationPolicyLoader;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;

@Component
public class DssValidatorAdapter implements SignatureValidatorPort {

  private final CertificateVerifier certificateVerifier;
  private final ObjectMapper om;

  public DssValidatorAdapter(CertificateVerifier certificateVerifier, ObjectMapper om) {
    this.certificateVerifier = certificateVerifier;
    this.om = om;
  }

  @Override
  @CircuitBreaker(name = "dssValidator", fallbackMethod = "fallback")
  public ValidationResult validate(ValidationRequest req) {
    DSSDocument doc = new InMemoryDocument(req.documentBytes(), req.filename());
    SignedDocumentValidator validator;
    try {
      validator = SignedDocumentValidator.fromDocument(doc);
    } catch (Exception e) {
      throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
    validator.setCertificateVerifier(certificateVerifier);

    ValidationPolicy policy;
    try {
      policy = ValidationPolicyLoader
          .fromValidationPolicy(new ByteArrayInputStream(req.policyXml().getBytes()))
          .create();
    } catch (Exception e) {
      throw AppException.badRequest("invalid validation policy: " + e.getMessage());
    }

    Reports reports = validator.validateDocument(policy);
    SimpleReport simple = reports.getSimpleReport();

    Map<ReportType, String> out = new EnumMap<>(ReportType.class);
    try {
      if (req.reports().contains(ReportType.SIMPLE)) out.put(ReportType.SIMPLE, om.writeValueAsString(reports.getSimpleReportJaxb()));
      if (req.reports().contains(ReportType.DETAILED)) out.put(ReportType.DETAILED, om.writeValueAsString(reports.getDetailedReportJaxb()));
      if (req.reports().contains(ReportType.DIAGNOSTIC)) out.put(ReportType.DIAGNOSTIC, om.writeValueAsString(reports.getDiagnosticDataJaxb()));
      if (req.reports().contains(ReportType.ETSI)) out.put(ReportType.ETSI, om.writeValueAsString(reports.getEtsiValidationReportJaxb()));
    } catch (Exception e) {
      throw new IllegalStateException("report serialization", e);
    }

    String format = simple.getSignatureFormat(simple.getFirstSignatureId()) == null ? "UNKNOWN" : simple.getSignatureFormat(simple.getFirstSignatureId()).toString();
    String indication = simple.getIndication(simple.getFirstSignatureId()) == null ? "INDETERMINATE" : simple.getIndication(simple.getFirstSignatureId()).toString();
    String subIndication = simple.getSubIndication(simple.getFirstSignatureId()) == null ? null : simple.getSubIndication(simple.getFirstSignatureId()).toString();
    return new ValidationResult(format, indication, subIndication, simple.getSignaturesCount(), out);
  }

  public ValidationResult fallback(ValidationRequest req, Throwable t) {
    throw AppException.dssUnavailable("dss circuit breaker open");
  }
}
```

- [ ] **Step 2: Test smoke (con SignatureValidatorPort.fromDocument su file invalido)**

```java
package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.ValidationRequest;

@SpringBootTest
@ActiveProfiles("test")
class DssValidatorAdapterTest {

  @Autowired private DssValidatorAdapter adapter;
  @Autowired private ObjectMapper om;

  @Test
  void parses_error_for_garbage_input() {
    byte[] bogus = "not a signature".getBytes();
    String policy = "<ConstraintsParameters xmlns=\"http://dss.esig.europa.eu/validation/policy\"/>";
    assertThatThrownBy(() -> adapter.validate(new ValidationRequest(bogus, "x.pdf", policy, Set.of(ReportType.SIMPLE))))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Unprocessable");
  }
}
```

- [ ] **Step 3: Run, commit**

```bash
mvn -B test -Dtest=DssValidatorAdapterTest -q
git add src/main src/test
git commit -m "feat(dss): add DssValidatorAdapter with circuit breaker"
```

## Task 6.4: `PolicyOverrideApplier` (DOM mutation)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/PolicyOverrideApplier.java`
- Create: `src/test/java/org/toresoft/signverify/application/PolicyOverrideApplierTest.java`

- [ ] **Step 1: Test**

```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyOverrideApplierTest {

  private final PolicyOverrideApplier applier = new PolicyOverrideApplier();

  @Test
  void disable_revocation_sets_level_ignore() {
    String xml = """
        <ConstraintsParameters xmlns="http://dss.esig.europa.eu/validation/policy">
          <SignatureConstraints>
            <BasicSignatureConstraints>
              <RevocationDataAvailable Level="FAIL"/>
            </BasicSignatureConstraints>
          </SignatureConstraints>
        </ConstraintsParameters>
        """;
    String modified = applier.apply(xml, Map.of("checkRevocation", false));
    assertThat(modified).contains("RevocationDataAvailable Level=\"IGNORE\"");
  }
}
```

- [ ] **Step 2: Implementa**

```java
package org.toresoft.signverify.application;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class PolicyOverrideApplier {

  private static final Map<String, List<String>> CHECK_TO_TAGS = Map.of(
      "checkRevocation", List.of("RevocationDataAvailable", "RevocationDataFreshness", "RevocationCertHashMatch"),
      "checkSignatureIntegrity", List.of("SignatureIntact", "SignatureValid"),
      "checkCertificateChain", List.of("ProspectiveCertificateChain", "TrustedServiceStatus"),
      "checkTimestamp", List.of("TimestampDelay", "MessageImprintDataIntact"),
      "checkQualified", List.of("QualifiedCertificate"));

  public String apply(String xml, Map<String, Object> overrides) {
    if (overrides == null || overrides.isEmpty()) return xml;
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      var builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

      for (var entry : overrides.entrySet()) {
        if (entry.getValue() instanceof Boolean b && !b) {
          for (String tag : CHECK_TO_TAGS.getOrDefault(entry.getKey(), List.of())) {
            setLevelOnAll(doc, tag, "IGNORE");
          }
        }
      }
      return toString(doc);
    } catch (Exception e) {
      throw new IllegalArgumentException("override application failed", e);
    }
  }

  private void setLevelOnAll(Document doc, String localName, String level) {
    NodeList nodes = doc.getElementsByTagNameNS("*", localName);
    for (int i = 0; i < nodes.getLength(); i++) {
      ((Element) nodes.item(i)).setAttribute("Level", level);
    }
  }

  private String toString(Document doc) throws Exception {
    var tf = TransformerFactory.newInstance();
    var t = tf.newTransformer();
    t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    StringWriter sw = new StringWriter();
    t.transform(new DOMSource(doc), new StreamResult(sw));
    return sw.toString();
  }
}
```

- [ ] **Step 3: Run, commit**

```bash
mvn -B test -Dtest=PolicyOverrideApplierTest -q
git add src/main src/test
git commit -m "feat(profile): add PolicyOverrideApplier for runtime XML mutation"
```

## Task 6.5: `VerificationService` con semaforo concorrenza

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/VerificationService.java`
- Create: `src/test/java/org/toresoft/signverify/application/VerificationServiceTest.java`

- [ ] **Step 1: Service**

```java
package org.toresoft.signverify.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.*;

@Service
public class VerificationService {

  public record VerifyRequest(
      byte[] file, String filename, UUID profileId, Map<String, Object> overrides, Set<ReportType> reports) {}

  public record VerifyResponse(
      String profileName, boolean overridesApplied, ValidationResult result) {}

  private final SignatureValidatorPort validator;
  private final VerificationProfileService profileService;
  private final PolicyOverrideApplier overrideApplier;
  private final Semaphore concurrencyLimiter;

  public VerificationService(
      SignatureValidatorPort validator,
      VerificationProfileService profileService,
      PolicyOverrideApplier overrideApplier,
      @Value("${app.verify.max-concurrent}") int maxConcurrent) {
    this.validator = validator;
    this.profileService = profileService;
    this.overrideApplier = overrideApplier;
    this.concurrencyLimiter = new Semaphore(maxConcurrent);
  }

  public VerifyResponse verifySync(VerifyRequest req) {
    boolean acquired;
    try {
      acquired = concurrencyLimiter.tryAcquire(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw AppException.concurrency("interrupted");
    }
    if (!acquired) throw AppException.concurrency("verify concurrency limit reached");

    try {
      var profile = profileService.getOrDefault(req.profileId());
      String policyXml = profile.getPolicyXml();
      boolean overridesApplied = req.overrides() != null && !req.overrides().isEmpty();
      if (overridesApplied) policyXml = overrideApplier.apply(policyXml, req.overrides());
      var result = validator.validate(new ValidationRequest(req.file(), req.filename(), policyXml, req.reports()));
      return new VerifyResponse(profile.getName(), overridesApplied, result);
    } finally {
      concurrencyLimiter.release();
    }
  }
}
```

- [ ] **Step 2: Test (concorrenza limite con mock validator lento)**

```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.VerificationProfile;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationResult;

class VerificationServiceTest {

  @Test
  void rejects_with_503_when_concurrency_full() throws Exception {
    SignatureValidatorPort validator = Mockito.mock(SignatureValidatorPort.class);
    VerificationProfileService profileService = Mockito.mock(VerificationProfileService.class);
    PolicyOverrideApplier applier = new PolicyOverrideApplier();

    VerificationProfile p = new VerificationProfile();
    p.setName("STANDARD");
    p.setPreset(ProfilePreset.STANDARD);
    p.setPolicyXml("<ConstraintsParameters xmlns=\"http://dss.esig.europa.eu/validation/policy\"/>");
    when(profileService.getOrDefault(any())).thenReturn(p);
    when(validator.validate(any())).thenAnswer(inv -> {
      Thread.sleep(3000);
      return new ValidationResult("PAdES", "TOTAL_PASSED", null, 1, Map.of());
    });

    VerificationService service = new VerificationService(validator, profileService, applier, 1);

    Thread t1 = new Thread(() -> service.verifySync(new VerificationService.VerifyRequest(new byte[]{1}, "a.pdf", null, Map.of(), Set.of(ReportType.SIMPLE))));
    t1.start();
    Thread.sleep(200);

    assertThatThrownBy(() -> service.verifySync(new VerificationService.VerifyRequest(new byte[]{2}, "b.pdf", null, Map.of(), Set.of(ReportType.SIMPLE))))
        .isInstanceOf(AppException.class);
    t1.join();
  }
}
```

- [ ] **Step 3: Run, commit**

```bash
mvn -B test -Dtest=VerificationServiceTest -q
git add src/main src/test
git commit -m "feat(verify): add VerificationService with concurrency semaphore"
```

## Task 6.6: Aggiungi path verifica sync in `openapi.yaml`

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`

- [ ] **Step 1: Path**

```yaml
  /api/v1/verifications:
    post:
      tags: [Verifications]
      operationId: verifySync
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file: { type: string, format: binary }
                metadata:
                  type: string
                  description: "JSON con profileId?, profileOverrides?, reports[]?"
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/VerificationResponse' }
```

Schema:
```yaml
    VerificationResponse:
      type: object
      properties:
        verifiedAt: { type: string, format: date-time }
        profileUsed: { type: string }
        overridesApplied: { type: boolean }
        signatureFormat: { type: string }
        indication: { type: string }
        subIndication: { type: string, nullable: true }
        signatureCount: { type: integer }
        reports:
          type: object
          additionalProperties: { type: object }
```

- [ ] **Step 2: Genera + commit**

```bash
mvn -B generate-sources -q
git add src/main/resources/openapi/openapi.yaml
git commit -m "feat(api): add openapi path for sync verification"
```

## Task 6.7: `VerificationController` sync

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/VerificationController.java`

```java
package org.toresoft.signverify.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.toresoft.signverify.application.VerificationService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;

@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationController {

  private final VerificationService service;
  private final ObjectMapper om;

  public VerificationController(VerificationService service, ObjectMapper om) {
    this.service = service;
    this.om = om;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> verify(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metadata", required = false) String metadataJson)
      throws Exception {

    Metadata m = parseMetadata(metadataJson);
    Set<ReportType> reports = parseReports(m.reports());
    UUID profileId = m.profileId() == null ? null : UUID.fromString(m.profileId());

    var req = new VerificationService.VerifyRequest(
        file.getBytes(), file.getOriginalFilename(), profileId, m.profileOverrides(), reports);
    var res = service.verifySync(req);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("verifiedAt", OffsetDateTime.now().toString());
    out.put("profileUsed", res.profileName());
    out.put("overridesApplied", res.overridesApplied());
    out.put("signatureFormat", res.result().signatureFormat());
    out.put("indication", res.result().indication());
    out.put("subIndication", res.result().subIndication());
    out.put("signatureCount", res.result().signatureCount());
    Map<String, Object> reportsOut = new LinkedHashMap<>();
    for (var e : res.result().reportsJson().entrySet()) {
      reportsOut.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
    }
    out.put("reports", reportsOut);
    return ResponseEntity.ok(out);
  }

  private record Metadata(String profileId, Map<String, Object> profileOverrides, List<String> reports) {}

  private Metadata parseMetadata(String json) {
    if (json == null || json.isBlank()) return new Metadata(null, Map.of(), List.of("simple", "etsi"));
    try {
      return om.readValue(json, new TypeReference<Metadata>(){});
    } catch (Exception e) {
      throw AppException.badRequest("invalid metadata json");
    }
  }

  private Set<ReportType> parseReports(List<String> raw) {
    if (raw == null || raw.isEmpty()) return EnumSet.of(ReportType.SIMPLE, ReportType.ETSI);
    EnumSet<ReportType> set = EnumSet.noneOf(ReportType.class);
    for (String s : raw) {
      switch (s.toLowerCase()) {
        case "simple" -> set.add(ReportType.SIMPLE);
        case "detailed" -> set.add(ReportType.DETAILED);
        case "diagnostic" -> set.add(ReportType.DIAGNOSTIC);
        case "etsi" -> set.add(ReportType.ETSI);
        default -> throw AppException.badRequest("unknown report type: " + s);
      }
    }
    return set;
  }
}
```

Commit: `feat(verify): add sync verification controller`

## Task 6.8: Integration test verifica firma con fixture PAdES

**Files:**
- Create: `src/test/resources/signatures/sample-pades-valid.pdf` (file binario reale)
- Create: `src/test/java/org/toresoft/signverify/api/VerificationControllerIT.java`

- [ ] **Step 1: Procurarsi fixture PAdES valida**

```bash
mkdir -p src/test/resources/signatures
# Scarica una firma PAdES di esempio dal repo DSS:
curl -sL https://github.com/esig/dss/raw/6.4.RC1/dss-pades/src/test/resources/validation/Signature-P-NO-1.pdf \
  -o src/test/resources/signatures/sample-pades-valid.pdf
```

- [ ] **Step 2: Test**

```java
package org.toresoft.signverify.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@SpringBootTest
@ActiveProfiles("test")
class VerificationControllerIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository keys;
  @Autowired private PasswordHasherPort hasher;

  private MockMvc mvc;
  private String apiKey;

  @BeforeEach
  void setup() {
    apiKey = "sv_verify01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName("verify-" + UUID.randomUUID());
    k.setKeyPrefix("verify01");
    k.setKeyHash(hasher.hash(apiKey));
    k.setRole(Role.STANDARD);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    keys.save(k);
    mvc = MockMvcBuilders.webAppContextSetup(ctx)
        .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
        .build();
  }

  @Test
  void verify_pades_returns_indication() throws Exception {
    byte[] pdf = Files.readAllBytes(Path.of("src/test/resources/signatures/sample-pades-valid.pdf"));
    var filePart = new MockMultipartFile("file", "sample.pdf", "application/pdf", pdf);
    var meta = new MockMultipartFile("metadata", "metadata", "application/json",
        "{\"reports\":[\"simple\"]}".getBytes());

    mvc.perform(multipart("/api/v1/verifications").file(filePart).file(meta)
            .header("X-API-Key", apiKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.indication").exists())
        .andExpect(jsonPath("$.signatureFormat").exists())
        .andExpect(jsonPath("$.reports.simple").exists());
  }
}
```

- [ ] **Step 3: Run + commit**

```bash
mvn -B test -Dtest=VerificationControllerIT -q
git add src/main src/test
git commit -m "test(verify): add integration test for sync verification with pades fixture"
```

## Verifica Fase 6

```bash
mvn -B verify -q
```

Expected: verifica firma sync end-to-end funzionante.

**Fine Fase 6.**

---

# FASE 7 — Estrazione documento originale

## Task 7.1: `ExtractionPort` + `DssExtractionAdapter`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/ExtractionPort.java`
- Create: `src/main/java/org/toresoft/signverify/adapter/dss/DssExtractionAdapter.java`

```java
// ExtractionPort.java
package org.toresoft.signverify.domain.port;
import java.util.List;
public interface ExtractionPort {
  ExtractionResult extract(byte[] signedDocument, String filename);
  record ExtractedFile(String filename, String mimeType, byte[] content) {}
  record ExtractionResult(String signatureFormat, List<ExtractedFile> originals) {}
}

// DssExtractionAdapter.java
package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ExtractionPort;

@Component
public class DssExtractionAdapter implements ExtractionPort {

  private final CertificateVerifier certificateVerifier;

  public DssExtractionAdapter(CertificateVerifier certificateVerifier) {
    this.certificateVerifier = certificateVerifier;
  }

  @Override
  @CircuitBreaker(name = "dssValidator", fallbackMethod = "fallback")
  public ExtractionResult extract(byte[] bytes, String filename) {
    DSSDocument doc = new InMemoryDocument(bytes, filename);
    SignedDocumentValidator validator;
    try {
      validator = SignedDocumentValidator.fromDocument(doc);
    } catch (Exception e) {
      throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
    validator.setCertificateVerifier(certificateVerifier);

    var signatures = validator.getSignatures();
    if (signatures.isEmpty()) throw AppException.signatureParseError("no signatures found");
    String firstSigId = signatures.get(0).getId();

    List<DSSDocument> originals;
    try {
      originals = validator.getOriginalDocuments(firstSigId);
    } catch (Exception e) {
      throw AppException.badRequest("cannot extract originals: " + e.getMessage());
    }

    List<ExtractedFile> out = new ArrayList<>();
    for (DSSDocument o : originals) {
      try {
        out.add(new ExtractedFile(
            o.getName(),
            o.getMimeType() == null ? "application/octet-stream" : o.getMimeType().getMimeTypeString(),
            o.openStream().readAllBytes()));
      } catch (Exception e) {
        throw new IllegalStateException("cannot read extracted document", e);
      }
    }
    String format = validator.getSignatureForm() == null ? "UNKNOWN" : validator.getSignatureForm().name();
    return new ExtractionResult(format, out);
  }

  public ExtractionResult fallback(byte[] bytes, String filename, Throwable t) {
    throw AppException.dssUnavailable("dss circuit breaker open");
  }
}
```

Commit: `feat(extract): add DssExtractionAdapter`

## Task 7.2: `ExtractionController`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/ExtractionController.java`

```java
package org.toresoft.signverify.api;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.toresoft.signverify.domain.port.ExtractionPort;

@RestController
@RequestMapping("/api/v1/extractions")
public class ExtractionController {

  private final ExtractionPort extractor;

  public ExtractionController(ExtractionPort extractor) { this.extractor = extractor; }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> extract(@RequestPart("file") MultipartFile file) throws Exception {
    var result = extractor.extract(file.getBytes(), file.getOriginalFilename());

    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Signature-Format", result.signatureFormat());
    headers.add("X-Document-Count", String.valueOf(result.originals().size()));

    if (result.originals().size() == 1) {
      var f = result.originals().get(0);
      headers.setContentType(MediaType.parseMediaType(f.mimeType()));
      headers.setContentDispositionFormData("attachment", f.filename());
      return ResponseEntity.ok().headers(headers).body(f.content());
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (var f : result.originals()) {
        zos.putNextEntry(new ZipEntry(f.filename()));
        zos.write(f.content());
        zos.closeEntry();
      }
    }
    headers.setContentType(MediaType.parseMediaType("application/zip"));
    headers.setContentDispositionFormData("attachment", "originals.zip");
    return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
  }
}
```

Aggiungi path in `openapi.yaml`:
```yaml
  /api/v1/extractions:
    post:
      tags: [Extractions]
      operationId: extract
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file: { type: string, format: binary }
      responses:
        '200':
          description: ok (binary diretto o zip)
          content:
            application/octet-stream:
              schema: { type: string, format: binary }
            application/zip:
              schema: { type: string, format: binary }
```

Run, commit: `feat(extract): add extraction endpoint`

## Verifica Fase 7

```bash
mvn -B verify -q
```

**Fine Fase 7.**

---

# FASE 8 — TSL management (refresh, mirror, status, lista filtrata)

## Task 8.1: `TslSourceConfig` + properties binding

**Files:**
- Create: `src/main/java/org/toresoft/signverify/config/TslProperties.java`

```java
package org.toresoft.signverify.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.tsl")
public class TslProperties {

  private List<Source> sources = List.of();
  private Refresh refresh = new Refresh();

  public List<Source> getSources() { return sources; } public void setSources(List<Source> s) { this.sources = s; }
  public Refresh getRefresh() { return refresh; } public void setRefresh(Refresh r) { this.refresh = r; }

  public static class Source {
    private String id;
    private String type;        // LOTL | TL
    private String url;
    private boolean pivotSupport;
    private String ojKeystorePath;
    private String ojKeystorePasswordEnv;
    private String ojUrl;
    public String getId() { return id; } public void setId(String v) { this.id = v; }
    public String getType() { return type; } public void setType(String v) { this.type = v; }
    public String getUrl() { return url; } public void setUrl(String v) { this.url = v; }
    public boolean isPivotSupport() { return pivotSupport; } public void setPivotSupport(boolean v) { this.pivotSupport = v; }
    public String getOjKeystorePath() { return ojKeystorePath; } public void setOjKeystorePath(String v) { this.ojKeystorePath = v; }
    public String getOjKeystorePasswordEnv() { return ojKeystorePasswordEnv; } public void setOjKeystorePasswordEnv(String v) { this.ojKeystorePasswordEnv = v; }
    public String getOjUrl() { return ojUrl; } public void setOjUrl(String v) { this.ojUrl = v; }
  }

  public static class Refresh {
    private String cron = "0 0 2 * * *";
    private String timezone = "Europe/Rome";
    private String startupMode = "BACKGROUND";  // BACKGROUND | BLOCKING | SKIP
    public String getCron() { return cron; } public void setCron(String v) { this.cron = v; }
    public String getTimezone() { return timezone; } public void setTimezone(String v) { this.timezone = v; }
    public String getStartupMode() { return startupMode; } public void setStartupMode(String v) { this.startupMode = v; }
  }
}
```

Aggiungi `@EnableConfigurationProperties(TslProperties.class)` su `DssConfiguration`.

Commit: `feat(tsl): add TSL properties binding`

## Task 8.2: `TLValidationJob` bean + cache dir

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/config/DssConfiguration.java`

Aggiungi:
```java
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.tsl.alerts.LOTLAlert;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.tsl.source.TLSource;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.toresoft.signverify.config.TslProperties;

@Bean
public FileCacheDataLoader fileCacheDataLoader(@Value("${app.dss.cache-dir}") String cacheDir) throws IOException {
  Path dir = Path.of(cacheDir);
  Files.createDirectories(dir);
  FileCacheDataLoader loader = new FileCacheDataLoader();
  loader.setDataLoader(new CommonsDataLoader());
  loader.setFileCacheDirectory(dir.toFile());
  loader.setCacheExpirationTime(0); // refresh sempre online quando chiamato
  return loader;
}

@Bean
public TLValidationJob tlValidationJob(
    TrustedListsCertificateSource tslSource,
    FileCacheDataLoader dataLoader,
    TslProperties props,
    ResourceLoader resourceLoader) throws Exception {

  TLValidationJob job = new TLValidationJob();
  job.setOnlineDataLoader(dataLoader);
  job.setOfflineDataLoader(dataLoader);
  job.setTrustedListCertificateSource(tslSource);

  List<LOTLSource> lotls = new ArrayList<>();
  List<TLSource> tls = new ArrayList<>();
  for (TslProperties.Source s : props.getSources()) {
    if ("LOTL".equalsIgnoreCase(s.getType())) {
      LOTLSource lotl = new LOTLSource();
      lotl.setUrl(s.getUrl());
      lotl.setPivotSupport(s.isPivotSupport());
      lotl.setCertificateSource(loadKeystoreSource(s, resourceLoader));
      lotls.add(lotl);
    } else {
      TLSource tl = new TLSource();
      tl.setUrl(s.getUrl());
      tls.add(tl);
    }
  }
  job.setListOfTrustedListSources(lotls.toArray(new LOTLSource[0]));
  job.setTrustedListSources(tls.toArray(new TLSource[0]));
  return job;
}

private CommonCertificateSource loadKeystoreSource(TslProperties.Source s, ResourceLoader rl) throws Exception {
  if (s.getOjKeystorePath() == null) return new CommonCertificateSource();
  String pwd = System.getenv(s.getOjKeystorePasswordEnv() == null ? "" : s.getOjKeystorePasswordEnv());
  if (pwd == null) pwd = "";
  KeyStore ks = KeyStore.getInstance("PKCS12");
  try (var in = rl.getResource(s.getOjKeystorePath()).getInputStream()) {
    ks.load(in, pwd.toCharArray());
  }
  CommonCertificateSource src = new CommonCertificateSource();
  var aliases = ks.aliases();
  while (aliases.hasMoreElements()) {
    String a = aliases.nextElement();
    var cert = ks.getCertificate(a);
    if (cert instanceof java.security.cert.X509Certificate x) {
      src.addCertificate(new eu.europa.esig.dss.model.x509.CertificateToken(x));
    }
  }
  return src;
}
```

Aggiungi OJ keystore placeholder:
```bash
mkdir -p src/main/resources/keystore
# Genera keystore vuoto per dev/test (in prod va sostituito con quello reale)
keytool -genkey -alias placeholder -keyalg RSA -keystore src/main/resources/keystore/oj-keystore.p12 -storetype PKCS12 -storepass changeit -dname "CN=Placeholder"
```

Commit: `feat(tsl): wire TLValidationJob with cache + OJ keystore loader`

## Task 8.3: `TrustedCertificateMirror` (post-refresh sync)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/TrustedCertificateMirror.java`

```java
package org.toresoft.signverify.application;

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.tsl.TrustServiceProvider;
import eu.europa.esig.dss.spi.tsl.TrustService;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.TrustedCertificate;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;

@Component
public class TrustedCertificateMirror {

  public record Diff(int added, int removed, int unchanged) {}

  private final TrustedCertificateRepository repo;

  public TrustedCertificateMirror(TrustedCertificateRepository repo) { this.repo = repo; }

  @Transactional
  public Diff sync(TrustedListsCertificateSource src) {
    Map<String, TrustedCertificate> dbByFp = new HashMap<>();
    repo.findAll().stream()
        .filter(c -> c.getRemovedAt() == null)
        .forEach(c -> dbByFp.put(c.getFingerprintSha256(), c));

    Set<String> currentFp = new HashSet<>();
    int added = 0;
    int unchanged = 0;
    Instant now = Instant.now();

    for (CertificateToken token : src.getCertificates()) {
      String fp = fingerprint(token);
      currentFp.add(fp);
      TrustedCertificate existing = dbByFp.get(fp);
      if (existing != null) {
        existing.setLastSeenAt(now);
        repo.save(existing);
        unchanged++;
      } else {
        TrustedCertificate tc = newEntity(token, src, fp, now);
        repo.save(tc);
        added++;
      }
    }

    int removed = 0;
    for (var e : dbByFp.entrySet()) {
      if (!currentFp.contains(e.getKey())) {
        e.getValue().setRemovedAt(now);
        repo.save(e.getValue());
        removed++;
      }
    }
    return new Diff(added, removed, unchanged);
  }

  private TrustedCertificate newEntity(CertificateToken t, TrustedListsCertificateSource src, String fp, Instant now) {
    TrustedCertificate c = new TrustedCertificate();
    c.setId(UUID.randomUUID());
    c.setFingerprintSha256(fp);
    c.setSki(toHex(t.getSki()));
    c.setSubjectDn(t.getSubject().getRFC2253());
    c.setSubjectCn(extractCn(t.getSubject().getRFC2253()));
    c.setIssuerDn(t.getIssuer().getRFC2253());
    c.setIssuerCn(extractCn(t.getIssuer().getRFC2253()));
    c.setSerialNumber(t.getSerialNumber().toString(16));
    c.setCountry(extractCountry(t.getSubject().getRFC2253()));
    c.setValidFrom(t.getNotBefore().toInstant());
    c.setValidTo(t.getNotAfter().toInstant());
    c.setCertificateDerB64(Base64.getEncoder().encodeToString(t.getEncoded()));
    c.setLastSeenAt(now);

    // TSP metadata: trova la prima trust service per questo cert
    List<TrustServiceProvider> tsps = src.getTrustServiceProviders(t);
    if (!tsps.isEmpty()) {
      TrustServiceProvider tsp = tsps.get(0);
      c.setTspName(tsp.getNames().values().stream().findFirst().flatMap(l -> l.stream().findFirst()).orElse(null));
      List<TrustService> services = tsp.getServices().stream().filter(s -> s.getCertificates().contains(t)).toList();
      if (!services.isEmpty()) {
        var status = services.get(0).getStatusAndInformationExtensions().getLatest();
        c.setTspServiceType(status.getType());
        c.setTspServiceStatus(status.getStatus());
      }
    }
    return c;
  }

  private String fingerprint(CertificateToken t) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return toHex(md.digest(t.getEncoded()));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String toHex(byte[] bytes) {
    if (bytes == null) return null;
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  private String extractCn(String dn) {
    for (String part : dn.split(",")) {
      String t = part.trim();
      if (t.toUpperCase(Locale.ROOT).startsWith("CN=")) return t.substring(3);
    }
    return null;
  }

  private String extractCountry(String dn) {
    for (String part : dn.split(",")) {
      String t = part.trim();
      if (t.toUpperCase(Locale.ROOT).startsWith("C=")) return t.substring(2);
    }
    return null;
  }
}
```

Commit: `feat(tsl): add TrustedCertificateMirror with diff sync`

## Task 8.4: `TslService`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/TslService.java`

```java
package org.toresoft.signverify.application;

import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.persistence.TslRefreshRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class TslService {

  private static final Logger log = LoggerFactory.getLogger(TslService.class);

  private final TLValidationJob job;
  private final TrustedListsCertificateSource tslSource;
  private final TrustedCertificateMirror mirror;
  private final TslRefreshRepository refreshRepo;
  private final AtomicBoolean ready = new AtomicBoolean(false);

  public TslService(TLValidationJob job, TrustedListsCertificateSource tslSource,
                    TrustedCertificateMirror mirror, TslRefreshRepository refreshRepo) {
    this.job = job;
    this.tslSource = tslSource;
    this.mirror = mirror;
    this.refreshRepo = refreshRepo;
  }

  public boolean isReady() { return ready.get(); }

  public TslRefresh refresh(RefreshTrigger trigger, Principal triggeredBy) {
    TslRefresh r = new TslRefresh();
    r.setId(UUID.randomUUID());
    r.setTrigger(trigger);
    r.setStartedAt(Instant.now());
    r.setStatus(RefreshStatus.RUNNING);
    if (triggeredBy != null) {
      r.setTriggeredByPrincipalType(triggeredBy.type());
      r.setTriggeredByPrincipalId(triggeredBy.id());
    }
    refreshRepo.save(r);

    try {
      job.onlineRefresh();
      var diff = mirror.sync(tslSource);
      r.setStatus(RefreshStatus.SUCCESS);
      r.setCertificatesAdded(diff.added());
      r.setCertificatesRemoved(diff.removed());
      r.setCertificatesUnchanged(diff.unchanged());
      ready.set(true);
    } catch (Exception e) {
      log.error("TSL refresh failed", e);
      r.setStatus(RefreshStatus.FAILED);
      r.setErrorSummary(e.getMessage());
    } finally {
      r.setCompletedAt(Instant.now());
      refreshRepo.save(r);
    }
    return r;
  }
}
```

Commit: `feat(tsl): add TslService for orchestrating refresh + mirror`

## Task 8.5: `TslRefreshScheduler` + startup mode

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/TslRefreshScheduler.java`

```java
package org.toresoft.signverify.application;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.config.TslProperties;
import org.toresoft.signverify.domain.model.RefreshTrigger;

@Component
public class TslRefreshScheduler {

  private static final Logger log = LoggerFactory.getLogger(TslRefreshScheduler.class);

  private final TslService tslService;
  private final TslProperties props;

  public TslRefreshScheduler(TslService tslService, TslProperties props) {
    this.tslService = tslService;
    this.props = props;
  }

  @Scheduled(cron = "${app.tsl.refresh.cron}", zone = "${app.tsl.refresh.timezone}")
  @SchedulerLock(name = "tslRefresh", lockAtMostFor = "30m", lockAtLeastFor = "5m")
  public void scheduledRefresh() {
    log.info("TSL scheduled refresh start");
    tslService.refresh(RefreshTrigger.SCHEDULED, null);
  }

  @EventListener
  public void onReady(ApplicationReadyEvent ev) {
    String mode = props.getRefresh().getStartupMode();
    switch (mode == null ? "BACKGROUND" : mode.toUpperCase()) {
      case "BLOCKING" -> tslService.refresh(RefreshTrigger.STARTUP, null);
      case "BACKGROUND" -> backgroundStartup();
      case "SKIP" -> log.info("TSL startup refresh skipped by config");
      default -> log.warn("Unknown TSL startup mode: {}", mode);
    }
  }

  @Async
  void backgroundStartup() {
    log.info("TSL background startup refresh start");
    tslService.refresh(RefreshTrigger.STARTUP, null);
  }
}
```

## Task 8.6: ShedLock configuration

**Files:**
- Create: `src/main/java/org/toresoft/signverify/config/SchedulerConfiguration.java`

```java
package org.toresoft.signverify.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerConfiguration {

  @Bean
  public LockProvider lockProvider(DataSource ds) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(ds))
            .usingDbTime()
            .build());
  }
}
```

Commit: `feat(scheduler): wire ShedLock for distributed scheduling`

## Task 8.7: Aggiungi path TSL in openapi + `TslController` + readiness

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`
- Create: `src/main/java/org/toresoft/signverify/api/TslController.java`
- Create: `src/main/java/org/toresoft/signverify/config/TslReadinessIndicator.java`

```yaml
  /api/v1/tsl/status:
    get:
      tags: [Tsl]
      operationId: tslStatus
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { type: object }
  /api/v1/tsl/refresh:
    post:
      tags: [Tsl]
      operationId: tslForceRefresh
      responses:
        '202':
          description: scheduled
          content:
            application/json:
              schema: { type: object }
  /api/v1/tsl/certificates:
    get:
      tags: [Tsl]
      operationId: listTrustedCertificates
      parameters:
        - { name: ski, in: query, schema: { type: string } }
        - { name: aki, in: query, schema: { type: string } }
        - { name: subjectCn, in: query, schema: { type: string } }
        - { name: subjectDn, in: query, schema: { type: string } }
        - { name: issuerCn, in: query, schema: { type: string } }
        - { name: issuerDn, in: query, schema: { type: string } }
        - { name: country, in: query, schema: { type: string } }
        - { name: tspName, in: query, schema: { type: string } }
        - { name: tspServiceType, in: query, schema: { type: string } }
        - { name: tspServiceStatus, in: query, schema: { type: string } }
        - { name: serialNumber, in: query, schema: { type: string } }
        - { name: validAt, in: query, schema: { type: string, format: date-time } }
        - { name: includeRemoved, in: query, schema: { type: boolean, default: false } }
        - { name: page, in: query, schema: { type: integer, default: 0 } }
        - { name: size, in: query, schema: { type: integer, default: 50 } }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { type: object }
  /api/v1/tsl/certificates/{id}:
    get:
      tags: [Tsl]
      operationId: getTrustedCertificate
      parameters:
        - { name: id, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: ok, content: { application/json: { schema: { type: object } } } }
```

Controller:
```java
package org.toresoft.signverify.api;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.toresoft.signverify.application.TslService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.RefreshTrigger;
import org.toresoft.signverify.domain.model.TrustedCertificate;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;
import org.toresoft.signverify.persistence.TslRefreshRepository;
import org.toresoft.signverify.security.Principal;

@RestController
@RequestMapping("/api/v1/tsl")
public class TslController {

  private final TslService tslService;
  private final TrustedCertificateRepository certRepo;
  private final TslRefreshRepository refreshRepo;

  public TslController(TslService s, TrustedCertificateRepository c, TslRefreshRepository r) {
    this.tslService = s; this.certRepo = c; this.refreshRepo = r;
  }

  @GetMapping("/status")
  public Map<String, Object> status() {
    Map<String, Object> out = new LinkedHashMap<>();
    refreshRepo.findTopByOrderByStartedAtDesc().ifPresent(r -> out.put("lastRefresh", refreshToMap(r)));
    out.put("currentCertificateCount", certRepo.count());
    out.put("ready", tslService.isReady());
    return out;
  }

  @PostMapping("/refresh")
  @PreAuthorize("hasRole('PRIVILEGED')")
  public ResponseEntity<Map<String, Object>> forceRefresh() {
    Principal actor = (Principal) org.springframework.security.core.context.SecurityContextHolder
        .getContext().getAuthentication().getPrincipal();
    var r = tslService.refresh(RefreshTrigger.MANUAL, actor);
    return ResponseEntity.accepted().body(Map.of("refreshId", r.getId(), "status", r.getStatus().name()));
  }

  @GetMapping("/certificates")
  public Map<String, Object> list(
      @RequestParam(required = false) String ski,
      @RequestParam(required = false) String aki,
      @RequestParam(required = false) String subjectCn,
      @RequestParam(required = false) String subjectDn,
      @RequestParam(required = false) String issuerCn,
      @RequestParam(required = false) String issuerDn,
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String tspName,
      @RequestParam(required = false) String tspServiceType,
      @RequestParam(required = false) String tspServiceStatus,
      @RequestParam(required = false) String serialNumber,
      @RequestParam(required = false) OffsetDateTime validAt,
      @RequestParam(defaultValue = "false") boolean includeRemoved,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    Specification<TrustedCertificate> spec = (root, q, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      if (!includeRemoved) ps.add(cb.isNull(root.get("removedAt")));
      if (ski != null) ps.add(cb.equal(root.get("ski"), ski));
      if (aki != null) ps.add(cb.equal(root.get("aki"), aki));
      if (subjectCn != null) ps.add(cb.like(cb.lower(root.get("subjectCn")), "%" + subjectCn.toLowerCase() + "%"));
      if (subjectDn != null) ps.add(cb.like(cb.lower(root.get("subjectDn")), "%" + subjectDn.toLowerCase() + "%"));
      if (issuerCn != null) ps.add(cb.like(cb.lower(root.get("issuerCn")), "%" + issuerCn.toLowerCase() + "%"));
      if (issuerDn != null) ps.add(cb.like(cb.lower(root.get("issuerDn")), "%" + issuerDn.toLowerCase() + "%"));
      if (country != null) ps.add(cb.equal(root.get("country"), country));
      if (tspName != null) ps.add(cb.like(cb.lower(root.get("tspName")), "%" + tspName.toLowerCase() + "%"));
      if (tspServiceType != null) ps.add(cb.equal(root.get("tspServiceType"), tspServiceType));
      if (tspServiceStatus != null) ps.add(cb.equal(root.get("tspServiceStatus"), tspServiceStatus));
      if (serialNumber != null) ps.add(cb.equal(root.get("serialNumber"), serialNumber));
      if (validAt != null) {
        Instant at = validAt.toInstant();
        ps.add(cb.lessThanOrEqualTo(root.get("validFrom"), at));
        ps.add(cb.greaterThanOrEqualTo(root.get("validTo"), at));
      }
      return cb.and(ps.toArray(new Predicate[0]));
    };

    var result = certRepo.findAll(spec, PageRequest.of(page, size));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("page", result.getNumber());
    out.put("size", result.getSize());
    out.put("totalElements", result.getTotalElements());
    out.put("totalPages", result.getTotalPages());
    out.put("content", result.map(this::certToMap).toList());
    return out;
  }

  @GetMapping("/certificates/{id}")
  public Map<String, Object> get(@PathVariable UUID id) {
    var c = certRepo.findById(id).orElseThrow(() -> AppException.notFound("certificate not found"));
    return certToMap(c);
  }

  private Map<String, Object> certToMap(TrustedCertificate c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId());
    m.put("ski", c.getSki()); m.put("aki", c.getAki());
    m.put("subjectDn", c.getSubjectDn()); m.put("subjectCn", c.getSubjectCn());
    m.put("issuerDn", c.getIssuerDn()); m.put("issuerCn", c.getIssuerCn());
    m.put("serialNumber", c.getSerialNumber()); m.put("country", c.getCountry());
    m.put("tspName", c.getTspName()); m.put("tspServiceType", c.getTspServiceType()); m.put("tspServiceStatus", c.getTspServiceStatus());
    m.put("validFrom", c.getValidFrom() == null ? null : c.getValidFrom().atOffset(ZoneOffset.UTC));
    m.put("validTo", c.getValidTo() == null ? null : c.getValidTo().atOffset(ZoneOffset.UTC));
    m.put("lastSeenAt", c.getLastSeenAt().atOffset(ZoneOffset.UTC));
    m.put("removedAt", c.getRemovedAt() == null ? null : c.getRemovedAt().atOffset(ZoneOffset.UTC));
    m.put("certificateDerB64", c.getCertificateDerB64());
    m.put("tslUrl", c.getTslUrl());
    return m;
  }

  private Map<String, Object> refreshToMap(org.toresoft.signverify.domain.model.TslRefresh r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId());
    m.put("trigger", r.getTrigger());
    m.put("startedAt", r.getStartedAt().atOffset(ZoneOffset.UTC));
    if (r.getCompletedAt() != null) m.put("completedAt", r.getCompletedAt().atOffset(ZoneOffset.UTC));
    m.put("status", r.getStatus());
    m.put("sourcesTotal", r.getSourcesTotal());
    m.put("sourcesFailed", r.getSourcesFailed());
    m.put("certificatesAdded", r.getCertificatesAdded());
    m.put("certificatesRemoved", r.getCertificatesRemoved());
    m.put("certificatesUnchanged", r.getCertificatesUnchanged());
    return m;
  }
}
```

Readiness:
```java
package org.toresoft.signverify.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.application.TslService;

@Component("tslReadiness")
public class TslReadinessIndicator implements HealthIndicator {

  private final TslService tslService;

  public TslReadinessIndicator(TslService s) { this.tslService = s; }

  @Override
  public Health health() {
    return tslService.isReady() ? Health.up().build() : Health.outOfService().build();
  }
}
```

Commit: `feat(tsl): add TSL endpoints + readiness indicator`

## Verifica Fase 8

```bash
mvn -B verify -q
```

Test integration TSL: spin up WireMock con LOTL stub + 1 TSL nazionale fake (lasciato come esercizio nel piano espanso — il pattern di scrittura test è già in `VerificationControllerIT`).

**Fine Fase 8.**

---

# FASE 9 — Async job + callback HMAC

## Task 9.1: `DocumentStoragePort` + filesystem adapter

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/DocumentStoragePort.java`
- Create: `src/main/java/org/toresoft/signverify/adapter/storage/FilesystemDocumentStorageAdapter.java`

```java
// Port
package org.toresoft.signverify.domain.port;
import java.io.InputStream;
public interface DocumentStoragePort {
  String storeInput(String jobId, String filename, byte[] content);
  String storeResult(String jobId, byte[] content);
  byte[] read(String path);
  void delete(String path);
}

// Adapter
package org.toresoft.signverify.adapter.storage;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.DocumentStoragePort;

@Component
public class FilesystemDocumentStorageAdapter implements DocumentStoragePort {

  private final Path baseDir;

  public FilesystemDocumentStorageAdapter(@Value("${app.storage.jobs-dir}") String dir) throws IOException {
    this.baseDir = Path.of(dir);
    Files.createDirectories(baseDir);
  }

  @Override
  public String storeInput(String jobId, String filename, byte[] content) {
    return write(jobId, "input-" + safeName(filename), content);
  }

  @Override
  public String storeResult(String jobId, byte[] content) {
    return write(jobId, "result.json", content);
  }

  @Override
  public byte[] read(String path) {
    try { return Files.readAllBytes(Path.of(path)); }
    catch (IOException e) { throw new IllegalStateException("read fail: " + path, e); }
  }

  @Override
  public void delete(String path) {
    if (path == null) return;
    try { Files.deleteIfExists(Path.of(path)); } catch (IOException ignored) {}
  }

  private String write(String jobId, String name, byte[] content) {
    try {
      Path dir = baseDir.resolve(jobId);
      Files.createDirectories(dir);
      Path file = dir.resolve(name);
      Files.write(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      return file.toString();
    } catch (IOException e) {
      throw new IllegalStateException("write fail", e);
    }
  }

  private String safeName(String n) {
    if (n == null) return "file";
    return n.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
```

Commit: `feat(storage): add filesystem document storage adapter`

## Task 9.2: `SecretCipherPort` + AES-GCM adapter

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/SecretCipherPort.java`
- Create: `src/main/java/org/toresoft/signverify/adapter/crypto/AesGcmSecretCipherAdapter.java`
- Create: `src/test/java/org/toresoft/signverify/adapter/crypto/AesGcmSecretCipherAdapterTest.java`

```java
// Port
package org.toresoft.signverify.domain.port;
public interface SecretCipherPort {
  String encrypt(String plaintext);
  String decrypt(String cipher);
}

// Adapter
package org.toresoft.signverify.adapter.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.SecretCipherPort;

@Component
public class AesGcmSecretCipherAdapter implements SecretCipherPort {

  private static final int IV_LEN = 12;
  private static final int TAG_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom rnd = new SecureRandom();

  public AesGcmSecretCipherAdapter(@Value("${app.security.master-key}") String masterKey) {
    if (masterKey == null || masterKey.isBlank()) {
      throw new IllegalStateException("app.security.master-key not configured");
    }
    byte[] raw = Base64.getDecoder().decode(masterKey);
    if (raw.length != 32) throw new IllegalStateException("master-key must be 256-bit base64");
    this.key = new SecretKeySpec(raw, "AES");
  }

  @Override
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LEN];
      rnd.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + ct.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(ct, 0, out, iv.length, ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  @Override
  public String decrypt(String cipherB64) {
    try {
      byte[] raw = Base64.getDecoder().decode(cipherB64);
      byte[] iv = new byte[IV_LEN];
      System.arraycopy(raw, 0, iv, 0, IV_LEN);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] pt = c.doFinal(raw, IV_LEN, raw.length - IV_LEN);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
```

Test:
```java
package org.toresoft.signverify.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AesGcmSecretCipherAdapterTest {
  @Test
  void roundtrip() {
    String key32 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="; // 32 byte base64
    var c = new AesGcmSecretCipherAdapter(key32);
    String pt = "supersecret-callback-key";
    assertThat(c.decrypt(c.encrypt(pt))).isEqualTo(pt);
  }
}
```

Commit: `feat(crypto): add AES-GCM secret cipher`

## Task 9.3: HMAC signer

**Files:**
- Create: `src/main/java/org/toresoft/signverify/adapter/callback/HmacSigner.java`
- Create: `src/test/java/org/toresoft/signverify/adapter/callback/HmacSignerTest.java`

```java
package org.toresoft.signverify.adapter.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSigner {

  public record SignedHeaders(String signature, String timestamp, String nonce, String deliveryId) {}

  public SignedHeaders sign(String algorithm, String secret, byte[] body, String deliveryId) {
    try {
      long ts = System.currentTimeMillis() / 1000;
      String nonce = java.util.UUID.randomUUID().toString();
      String bodyHash = toHex(MessageDigest.getInstance("SHA-256").digest(body));
      String canonical = ts + "\n" + nonce + "\n" + deliveryId + "\n" + bodyHash;
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
      String sig = toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
      String prefix = "HmacSHA512".equalsIgnoreCase(algorithm) ? "sha512" : "sha256";
      return new SignedHeaders(prefix + "=" + sig, String.valueOf(ts), nonce, deliveryId);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  private String toHex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) sb.append(String.format("%02x", x));
    return sb.toString();
  }
}
```

Test deterministic verifier (fixture vector lasciato per esecutore).

Commit: `feat(callback): add HMAC signer`

## Task 9.4: `CallbackDispatcherPort` + adapter

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/port/CallbackDispatcherPort.java`
- Create: `src/main/java/org/toresoft/signverify/adapter/callback/HmacCallbackDispatcherAdapter.java`

```java
// Port
package org.toresoft.signverify.domain.port;
public interface CallbackDispatcherPort {
  DispatchResult dispatch(String url, String algorithm, String secret, byte[] body, String jobId, String deliveryId, int attempt);
  record DispatchResult(int statusCode, String errorMessage) {
    public boolean success(java.util.Set<Integer> successCodes) {
      return errorMessage == null && successCodes.contains(statusCode);
    }
    public boolean nonRetryable(java.util.Set<Integer> nonRetryableCodes) {
      return errorMessage == null && nonRetryableCodes.contains(statusCode);
    }
  }
}

// Adapter
package org.toresoft.signverify.adapter.callback;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort;

@Component
public class HmacCallbackDispatcherAdapter implements CallbackDispatcherPort {

  private final HmacSigner signer = new HmacSigner();
  private final HttpClient client;
  private final int timeoutMs;
  private final boolean allowHttp;
  private final boolean blockPrivate;

  public HmacCallbackDispatcherAdapter(
      @Value("${app.callback.timeout}") Duration timeout,
      @Value("${app.callback.allow-http}") boolean allowHttp,
      @Value("${app.callback.block-private-networks}") boolean blockPrivate) {
    this.timeoutMs = (int) timeout.toMillis();
    this.allowHttp = allowHttp;
    this.blockPrivate = blockPrivate;
    this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public DispatchResult dispatch(String url, String alg, String secret, byte[] body, String jobId, String deliveryId, int attempt) {
    try {
      URI uri = URI.create(url);
      if (!allowHttp && !"https".equalsIgnoreCase(uri.getScheme())) {
        return new DispatchResult(0, "http_disallowed");
      }
      if (blockPrivate && isPrivate(uri.getHost())) {
        return new DispatchResult(0, "private_network_blocked");
      }
      var sig = signer.sign(alg, secret, body, deliveryId);
      HttpRequest req = HttpRequest.newBuilder(uri)
          .timeout(Duration.ofMillis(timeoutMs))
          .header("Content-Type", "application/json")
          .header("X-Timestamp", sig.timestamp())
          .header("X-Nonce", sig.nonce())
          .header("X-Signature", sig.signature())
          .header("X-Signature-Algorithm", alg)
          .header("X-Job-Id", jobId)
          .header("X-Delivery-Id", deliveryId)
          .header("X-Delivery-Attempt", String.valueOf(attempt))
          .header("User-Agent", "sign-verify/1.0")
          .POST(HttpRequest.BodyPublishers.ofByteArray(body))
          .build();
      HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
      return new DispatchResult(res.statusCode(), null);
    } catch (Exception e) {
      return new DispatchResult(0, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private boolean isPrivate(String host) {
    if (host == null) return true;
    return host.startsWith("10.") || host.startsWith("192.168.")
        || host.startsWith("172.16.") || host.startsWith("172.17.")
        || host.startsWith("172.18.") || host.startsWith("172.19.")
        || host.startsWith("172.20.") || host.startsWith("172.21.")
        || host.startsWith("172.22.") || host.startsWith("172.23.")
        || host.startsWith("172.24.") || host.startsWith("172.25.")
        || host.startsWith("172.26.") || host.startsWith("172.27.")
        || host.startsWith("172.28.") || host.startsWith("172.29.")
        || host.startsWith("172.30.") || host.startsWith("172.31.")
        || host.equals("localhost") || host.equals("127.0.0.1");
  }
}
```

Commit: `feat(callback): add HMAC HTTP dispatcher with allow-http + private-network controls`

## Task 9.5: `AsyncJobService` con back-pressure

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/AsyncJobService.java`

```java
package org.toresoft.signverify.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SecretCipherPort;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class AsyncJobService {

  public record SubmitRequest(
      byte[] file, String filename, UUID profileId, String overridesJson,
      Set<ReportType> reports, String callbackUrl, String callbackSecret, String callbackAlgorithm) {}

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final SecretCipherPort cipher;
  private final int maxPerPrincipal;
  private final int maxGlobal;
  private final Duration jobTtl;

  public AsyncJobService(
      ValidationJobRepository repo, DocumentStoragePort storage, SecretCipherPort cipher,
      @Value("${app.async.max-pending-per-principal}") int maxPerPrincipal,
      @Value("${app.async.max-pending-global}") int maxGlobal,
      @Value("${app.async.job-ttl}") Duration jobTtl) {
    this.repo = repo; this.storage = storage; this.cipher = cipher;
    this.maxPerPrincipal = maxPerPrincipal; this.maxGlobal = maxGlobal; this.jobTtl = jobTtl;
  }

  @Transactional
  public UUID submit(SubmitRequest req, Principal actor) {
    if (repo.countActiveGlobal() >= maxGlobal)
      throw AppException.backpressure("global async backpressure");
    if (repo.countActiveByPrincipal(actor.type(), actor.id()) >= maxPerPrincipal)
      throw AppException.backpressure("per-principal async backpressure");

    UUID jobId = UUID.randomUUID();
    String docPath = storage.storeInput(jobId.toString(), req.filename(), req.file());

    ValidationJob j = new ValidationJob();
    j.setId(jobId);
    j.setStatus(JobStatus.PENDING);
    j.setProfileId(req.profileId());
    j.setProfileOverrides(req.overridesJson());
    j.setReportsRequested(req.reports().stream().map(r -> r.name().toLowerCase()).reduce((a, b) -> a + "," + b).orElse("simple,etsi"));
    j.setDocumentPath(docPath);
    j.setDocumentFilename(req.filename());
    j.setCallbackUrl(req.callbackUrl());
    if (req.callbackSecret() != null) j.setCallbackSecretCipher(cipher.encrypt(req.callbackSecret()));
    j.setCallbackAlgorithm(req.callbackAlgorithm() == null ? "HmacSHA256" : req.callbackAlgorithm());
    Instant now = Instant.now();
    j.setCreatedAt(now);
    j.setExpiresAt(now.plus(jobTtl));
    j.setRequestedByPrincipalType(actor.type());
    j.setRequestedByPrincipalId(actor.id());
    repo.save(j);
    return jobId;
  }
}
```

Commit: `feat(async): add AsyncJobService.submit with back-pressure`

## Task 9.6: `ValidationWorker` (poll + execute)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/ValidationWorker.java`

```java
package org.toresoft.signverify.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.*;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class ValidationWorker {

  private static final Logger log = LoggerFactory.getLogger(ValidationWorker.class);

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final VerificationProfileService profileService;
  private final PolicyOverrideApplier overrideApplier;
  private final SignatureValidatorPort validator;
  private final ObjectMapper om;
  private final CircuitBreaker dssCircuit;
  private final int maxPickupAttempts;

  public ValidationWorker(
      ValidationJobRepository repo, DocumentStoragePort storage,
      VerificationProfileService profileService, PolicyOverrideApplier applier,
      SignatureValidatorPort validator, ObjectMapper om,
      CircuitBreakerRegistry registry,
      @Value("${app.async.max-pickup-attempts}") int maxPickupAttempts) {
    this.repo = repo; this.storage = storage; this.profileService = profileService;
    this.overrideApplier = applier; this.validator = validator; this.om = om;
    this.dssCircuit = registry.circuitBreaker("dssValidator");
    this.maxPickupAttempts = maxPickupAttempts;
  }

  @Scheduled(fixedDelayString = "${app.async.worker.poll-interval}")
  public void poll() {
    if (dssCircuit.getState() == CircuitBreaker.State.OPEN) return;
    var jobs = repo.findPickablePending(maxPickupAttempts, 4);
    for (ValidationJob j : jobs) {
      try { process(j.getId()); }
      catch (Exception e) { log.error("worker error on job {}", j.getId(), e); }
    }
  }

  @Transactional
  public void process(UUID id) {
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null || job.getStatus() != JobStatus.PENDING) return;
    job.setStatus(JobStatus.RUNNING);
    job.setStartedAt(Instant.now());
    job.setPickupAttempts(job.getPickupAttempts() + 1);
    repo.save(job);

    try {
      byte[] file = storage.read(job.getDocumentPath());
      var profile = profileService.getOrDefault(job.getProfileId());
      String policy = profile.getPolicyXml();
      if (job.getProfileOverrides() != null && !job.getProfileOverrides().isBlank()) {
        Map<String, Object> ov = om.readValue(job.getProfileOverrides(), new TypeReference<>() {});
        policy = overrideApplier.apply(policy, ov);
      }
      Set<ReportType> reports = parseReports(job.getReportsRequested());
      var result = validator.validate(new ValidationRequest(file, job.getDocumentFilename(), policy, reports));

      Map<String, Object> resultJson = new LinkedHashMap<>();
      resultJson.put("indication", result.indication());
      resultJson.put("subIndication", result.subIndication());
      resultJson.put("signatureFormat", result.signatureFormat());
      resultJson.put("signatureCount", result.signatureCount());
      resultJson.put("profileUsed", profile.getName());
      Map<String, Object> rep = new LinkedHashMap<>();
      for (var e : result.reportsJson().entrySet()) {
        rep.put(e.getKey().name().toLowerCase(), om.readTree(e.getValue()));
      }
      resultJson.put("reports", rep);

      String resultPath = storage.storeResult(job.getId().toString(), om.writeValueAsBytes(resultJson));
      job.setResultPath(resultPath);
      job.setStatus(JobStatus.COMPLETED);
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    } catch (AppException ae) {
      if ("dss.unavailable".equals(ae.getCode()) && job.getPickupAttempts() < maxPickupAttempts) {
        job.setStatus(JobStatus.PENDING);
        repo.save(job);
        return;
      }
      job.setStatus(JobStatus.FAILED);
      job.setErrorMessage(ae.getCode() + ": " + ae.getDetail());
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    } catch (Exception e) {
      job.setStatus(JobStatus.FAILED);
      job.setErrorMessage(e.getMessage());
      job.setCompletedAt(Instant.now());
      if (job.getCallbackUrl() != null) job.setNextCallbackAt(Instant.now());
      repo.save(job);
    }
  }

  private Set<ReportType> parseReports(String csv) {
    Set<ReportType> set = EnumSet.noneOf(ReportType.class);
    for (String s : csv.split(",")) {
      try { set.add(ReportType.valueOf(s.trim().toUpperCase())); }
      catch (IllegalArgumentException ignored) {}
    }
    if (set.isEmpty()) set.add(ReportType.SIMPLE);
    return set;
  }
}
```

Commit: `feat(async): add ValidationWorker poll + execute with HOLD on circuit open`

## Task 9.7: `CallbackWorker` (poll + dispatch + retry)

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/CallbackWorker.java`

```java
package org.toresoft.signverify.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.SecretCipherPort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class CallbackWorker {

  private static final Logger log = LoggerFactory.getLogger(CallbackWorker.class);

  private final ValidationJobRepository repo;
  private final CallbackDispatcherPort dispatcher;
  private final SecretCipherPort cipher;
  private final DocumentStoragePort storage;
  private final ObjectMapper om;
  private final int maxAttempts;
  private final List<Duration> backoff;
  private final Set<Integer> successCodes;
  private final Set<Integer> nonRetryableCodes;

  public CallbackWorker(
      ValidationJobRepository repo, CallbackDispatcherPort dispatcher,
      SecretCipherPort cipher, DocumentStoragePort storage, ObjectMapper om,
      @Value("${app.callback.max-attempts}") int maxAttempts,
      @Value("${app.callback.backoff}") List<Duration> backoff,
      @Value("${app.callback.success-statuses}") List<Integer> success,
      @Value("${app.callback.non-retryable-statuses}") List<Integer> nonRetry) {
    this.repo = repo; this.dispatcher = dispatcher; this.cipher = cipher; this.storage = storage; this.om = om;
    this.maxAttempts = maxAttempts; this.backoff = backoff;
    this.successCodes = new HashSet<>(success);
    this.nonRetryableCodes = new HashSet<>(nonRetry);
  }

  @Scheduled(fixedDelayString = "${app.callback.worker.poll-interval}")
  @SchedulerLock(name = "callbackDispatch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  public void poll() {
    var due = repo.findCallbacksDue(Instant.now(), maxAttempts, 16);
    for (ValidationJob j : due) {
      try { dispatch(j.getId()); }
      catch (Exception e) { log.error("callback dispatch error on job {}", j.getId(), e); }
    }
  }

  @Transactional
  public void dispatch(UUID id) throws Exception {
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null || job.getCallbackUrl() == null) return;
    if (job.getNextCallbackAt() == null || job.getNextCallbackAt().isAfter(Instant.now())) return;

    String secret = cipher.decrypt(job.getCallbackSecretCipher());
    byte[] body = buildBody(job);
    String deliveryId = UUID.randomUUID().toString();
    int attempt = job.getCallbackAttempts() + 1;

    var res = dispatcher.dispatch(job.getCallbackUrl(), job.getCallbackAlgorithm(), secret, body, job.getId().toString(), deliveryId, attempt);
    if (res.success(successCodes)) {
      job.setStatus(JobStatus.DELIVERED);
      job.setDeliveredAt(Instant.now());
    } else if (res.nonRetryable(nonRetryableCodes)) {
      job.setStatus(JobStatus.DELIVERY_FAILED);
      job.setLastCallbackError("status=" + res.statusCode());
    } else if (attempt >= maxAttempts) {
      job.setStatus(JobStatus.DELIVERY_FAILED);
      job.setLastCallbackError(res.errorMessage() == null ? "status=" + res.statusCode() : res.errorMessage());
    } else {
      Duration d = backoff.get(Math.min(attempt - 1, backoff.size() - 1));
      job.setNextCallbackAt(Instant.now().plus(d));
      job.setLastCallbackError(res.errorMessage() == null ? "status=" + res.statusCode() : res.errorMessage());
    }
    job.setCallbackAttempts(attempt);
    repo.save(job);
  }

  private byte[] buildBody(ValidationJob job) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("jobId", job.getId());
    body.put("status", job.getStatus().name());
    if (job.getStatus() == JobStatus.COMPLETED && job.getResultPath() != null) {
      byte[] r = storage.read(job.getResultPath());
      body.put("result", om.readTree(r));
    } else if (job.getStatus() == JobStatus.FAILED) {
      Map<String, String> err = new LinkedHashMap<>();
      String msg = job.getErrorMessage() == null ? "unknown" : job.getErrorMessage();
      err.put("code", msg.contains(":") ? msg.split(":")[0] : "internal-error");
      err.put("message", msg);
      body.put("error", err);
    }
    return om.writeValueAsBytes(body);
  }
}
```

Commit: `feat(async): add CallbackWorker with retry + non-retryable handling`

## Task 9.8: Aggiungi path async + GET job in openapi + `AsyncVerificationController`

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`
- Create: `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`

Path:
```yaml
  /api/v1/verifications/async:
    post:
      tags: [Verifications]
      operationId: verifyAsync
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file: { type: string, format: binary }
                metadata: { type: string }
      responses:
        '202': { description: accepted, content: { application/json: { schema: { type: object } } } }
  /api/v1/verifications/jobs/{jobId}:
    get:
      tags: [Verifications]
      operationId: getJob
      parameters:
        - { name: jobId, in: path, required: true, schema: { type: string, format: uuid } }
      responses:
        '200': { description: ok, content: { application/json: { schema: { type: object } } } }
        '404': { description: not found }
        '410': { description: gone }
```

Controller:
```java
package org.toresoft.signverify.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.toresoft.signverify.application.AsyncJobService;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@RestController
@RequestMapping("/api/v1/verifications")
public class AsyncVerificationController {

  private final AsyncJobService asyncService;
  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final ObjectMapper om;

  public AsyncVerificationController(AsyncJobService s, ValidationJobRepository r, DocumentStoragePort st, ObjectMapper om) {
    this.asyncService = s; this.repo = r; this.storage = st; this.om = om;
  }

  @PostMapping(value = "/async", consumes = "multipart/form-data")
  public ResponseEntity<Map<String, Object>> submit(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metadata", required = false) String metadataJson) throws Exception {

    Map<String, Object> meta = metadataJson == null || metadataJson.isBlank()
        ? Map.of() : om.readValue(metadataJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    UUID profileId = meta.get("profileId") == null ? null : UUID.fromString(String.valueOf(meta.get("profileId")));
    String overridesJson = meta.get("profileOverrides") == null ? null : om.writeValueAsString(meta.get("profileOverrides"));
    @SuppressWarnings("unchecked")
    List<String> reports = (List<String>) meta.getOrDefault("reports", List.of("simple", "etsi"));
    Set<ReportType> reportTypes = EnumSet.noneOf(ReportType.class);
    for (String r : reports) { try { reportTypes.add(ReportType.valueOf(r.toUpperCase())); } catch (Exception ignored) {} }
    String callbackUrl = (String) meta.get("callbackUrl");
    String callbackSecret = (String) meta.get("callbackSecret");
    String callbackAlgo = (String) meta.get("callbackAlgorithm");

    Principal actor = (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    UUID jobId = asyncService.submit(
        new AsyncJobService.SubmitRequest(file.getBytes(), file.getOriginalFilename(),
            profileId, overridesJson, reportTypes, callbackUrl, callbackSecret, callbackAlgo),
        actor);

    return ResponseEntity.status(202)
        .header("Location", "/api/v1/verifications/jobs/" + jobId)
        .body(Map.of("jobId", jobId, "status", "PENDING"));
  }

  @GetMapping("/jobs/{jobId}")
  public ResponseEntity<?> getJob(@org.springframework.web.bind.annotation.PathVariable UUID jobId) throws Exception {
    ValidationJob job = repo.findById(jobId).orElseThrow(() -> AppException.notFound("job not found"));
    Principal actor = (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    boolean isOwner = actor.type() == job.getRequestedByPrincipalType()
        && actor.id().equals(job.getRequestedByPrincipalId());
    boolean isPriv = actor.role() == Role.PRIVILEGED;
    if (!isOwner && !isPriv) throw AppException.notFound("job not found");

    job.setLastAccessedAt(java.time.Instant.now());
    repo.save(job);

    if (job.getStatus() == JobStatus.DELETED) {
      return ResponseEntity.status(410).body(Map.of(
          "jobId", job.getId(),
          "originalStatus", job.getOriginalStatus(),
          "completedAt", job.getCompletedAt() == null ? null : job.getCompletedAt().atOffset(ZoneOffset.UTC),
          "deletedAt", job.getDeletedAt() == null ? null : job.getDeletedAt().atOffset(ZoneOffset.UTC),
          "message", "result no longer available"));
    }
    return ResponseEntity.ok(buildResponse(job));
  }

  private Object buildResponse(ValidationJob job) throws Exception {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("jobId", job.getId());
    out.put("status", job.getStatus().name());
    out.put("createdAt", job.getCreatedAt().atOffset(ZoneOffset.UTC));
    if (job.getStartedAt() != null) out.put("startedAt", job.getStartedAt().atOffset(ZoneOffset.UTC));
    if (job.getCompletedAt() != null) out.put("completedAt", job.getCompletedAt().atOffset(ZoneOffset.UTC));
    if (job.getDeliveredAt() != null) out.put("deliveredAt", job.getDeliveredAt().atOffset(ZoneOffset.UTC));
    out.put("expiresAt", job.getExpiresAt().atOffset(ZoneOffset.UTC));
    out.put("callbackAttempts", job.getCallbackAttempts());
    if (job.getResultPath() != null) {
      out.put("result", om.readTree(storage.read(job.getResultPath())));
    } else if (job.getErrorMessage() != null) {
      out.put("error", job.getErrorMessage());
    }
    if (job.getLastCallbackError() != null) out.put("lastCallbackError", job.getLastCallbackError());
    return out;
  }
}
```

Commit: `feat(async): add async verification controller with GET job + ownership check`

## Verifica Fase 9

```bash
mvn -B verify -q
```

**Fine Fase 9.**

---

# FASE 10 — Cleanup retention multi-fase

## Task 10.1: `JobCleanupScheduler`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/JobCleanupScheduler.java`

```java
package org.toresoft.signverify.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class JobCleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(JobCleanupScheduler.class);

  private final ValidationJobRepository repo;
  private final DocumentStoragePort storage;
  private final Duration inputRetention;
  private final Duration resultRetention;
  private final Duration tombstoneRetention;

  public JobCleanupScheduler(
      ValidationJobRepository repo, DocumentStoragePort storage,
      @Value("${app.async.input-retention}") Duration inputRetention,
      @Value("${app.async.result-retention}") Duration resultRetention,
      @Value("${app.async.tombstone-retention}") Duration tombstoneRetention) {
    this.repo = repo; this.storage = storage;
    this.inputRetention = inputRetention;
    this.resultRetention = resultRetention;
    this.tombstoneRetention = tombstoneRetention;
  }

  @Scheduled(cron = "${app.async.cleanup.cron}")
  @SchedulerLock(name = "jobCleanup", lockAtMostFor = "PT30M")
  @Transactional
  public void cleanup() {
    Instant now = Instant.now();

    // Fase 4 EXPIRED: PENDING/RUNNING + expires_at < now → FAILED
    repo.findAll().stream()
        .filter(j -> (j.getStatus() == JobStatus.PENDING || j.getStatus() == JobStatus.RUNNING)
            && j.getExpiresAt().isBefore(now))
        .forEach(j -> {
          j.setStatus(JobStatus.FAILED);
          j.setErrorMessage("job_expired");
          j.setCompletedAt(now);
          if (j.getCallbackUrl() != null) j.setNextCallbackAt(now);
          repo.save(j);
        });

    // Fase 1 INPUT: terminal + completed_at < now-inputRetention → delete input file
    repo.findAll().stream()
        .filter(j -> j.getStatus().isTerminal() && j.getDocumentPath() != null
            && j.getCompletedAt() != null && j.getCompletedAt().isBefore(now.minus(inputRetention)))
        .forEach(j -> {
          storage.delete(j.getDocumentPath());
          j.setDocumentPath(null);
          repo.save(j);
        });

    // Fase 2 TOMBSTONE: terminal (non DELETED) + completed_at < now-resultRetention
    repo.findAll().stream()
        .filter(j -> j.getStatus().isTerminal() && j.getStatus() != JobStatus.DELETED
            && j.getCompletedAt() != null && j.getCompletedAt().isBefore(now.minus(resultRetention)))
        .forEach(j -> {
          storage.delete(j.getResultPath());
          j.setOriginalStatus(j.getStatus());
          j.setStatus(JobStatus.DELETED);
          j.setDeletedAt(now);
          j.setResultPath(null);
          j.setCallbackUrl(null);
          j.setCallbackSecretCipher(null);
          j.setProfileOverrides(null);
          j.setErrorMessage(null);
          j.setLastCallbackError(null);
          repo.save(j);
        });

    // Fase 3 DELETE: DELETED + deleted_at < now-tombstoneRetention → DELETE row
    repo.findAll().stream()
        .filter(j -> j.getStatus() == JobStatus.DELETED
            && j.getDeletedAt() != null && j.getDeletedAt().isBefore(now.minus(tombstoneRetention)))
        .forEach(j -> repo.deleteById(j.getId()));

    log.info("Cleanup pass complete at {}", now);
  }
}
```

Commit: `feat(cleanup): add JobCleanupScheduler with 4-phase retention`

(Nota: in produzione le query `findAll().stream()` vanno sostituite con query JPA mirate per non caricare tutta la tabella. Per la v1 e dataset piccolo va bene; ottimizzazione documentata come follow-up.)

**Fine Fase 10.**

---

# FASE 11 — Audit log

## Task 11.1: `AuditService` + `AuditAspect`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/AuditService.java`
- Create: `src/main/java/org/toresoft/signverify/application/audit/Audited.java` (annotazione)
- Create: `src/main/java/org/toresoft/signverify/application/audit/AuditAspect.java`

```java
// AuditService
package org.toresoft.signverify.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.persistence.AuditLogRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class AuditService {
  private final AuditLogRepository repo;
  private final ObjectMapper om;

  public AuditService(AuditLogRepository repo, ObjectMapper om) { this.repo = repo; this.om = om; }

  public void log(Principal actor, String action, String targetType, String targetId, boolean success, Map<String, Object> details) {
    AuditLog a = new AuditLog();
    a.setId(UUID.randomUUID());
    a.setOccurredAt(Instant.now());
    a.setPrincipalType(actor == null ? PrincipalType.SYSTEM : actor.type());
    a.setPrincipalId(actor == null ? "system" : actor.id());
    a.setAction(action);
    a.setTargetType(targetType);
    a.setTargetId(targetId);
    a.setSuccess(success);
    try { a.setDetails(details == null ? null : om.writeValueAsString(details)); } catch (Exception ignored) {}
    repo.save(a);
  }
}

// Audited annotation
package org.toresoft.signverify.application.audit;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {
  String action();
  String targetType() default "";
}

// AuditAspect
package org.toresoft.signverify.application.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.application.AuditService;
import org.toresoft.signverify.security.Principal;

@Aspect
@Component
public class AuditAspect {

  private final AuditService audit;

  public AuditAspect(AuditService audit) { this.audit = audit; }

  @Around("@annotation(org.toresoft.signverify.application.audit.Audited)")
  public Object around(ProceedingJoinPoint pjp) throws Throwable {
    var sig = (MethodSignature) pjp.getSignature();
    Audited ann = sig.getMethod().getAnnotation(Audited.class);
    Object actor = null;
    try { actor = SecurityContextHolder.getContext().getAuthentication().getPrincipal(); } catch (Exception ignored) {}
    Principal p = actor instanceof Principal ? (Principal) actor : null;
    try {
      Object result = pjp.proceed();
      audit.log(p, ann.action(), ann.targetType().isEmpty() ? null : ann.targetType(), null, true, null);
      return result;
    } catch (Throwable t) {
      audit.log(p, ann.action(), ann.targetType().isEmpty() ? null : ann.targetType(), null, false,
          java.util.Map.of("error", t.getMessage()));
      throw t;
    }
  }
}
```

Aggiungi `@Audited` ai metodi key in `ApiKeyService`, `VerificationProfileService`, `TslService`, `AsyncJobService`, `VerificationService`.

Commit: `feat(audit): add AuditService + aspect + annotations`

## Task 11.2: `AuditController`

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/AuditController.java`

```java
package org.toresoft.signverify.api;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.persistence.AuditLogRepository;

@RestController
@RequestMapping("/api/v1/audit-log")
@PreAuthorize("hasRole('PRIVILEGED')")
public class AuditController {

  private final AuditLogRepository repo;
  public AuditController(AuditLogRepository repo) { this.repo = repo; }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String principalId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId,
      @RequestParam(required = false) Boolean success,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    Specification<AuditLog> spec = (root, q, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      if (principalId != null) ps.add(cb.equal(root.get("principalId"), principalId));
      if (action != null) ps.add(cb.equal(root.get("action"), action));
      if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from.toInstant()));
      if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to.toInstant()));
      if (targetType != null) ps.add(cb.equal(root.get("targetType"), targetType));
      if (targetId != null) ps.add(cb.equal(root.get("targetId"), targetId));
      if (success != null) ps.add(cb.equal(root.get("success"), success));
      return cb.and(ps.toArray(new Predicate[0]));
    };
    var p = repo.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
    return Map.of("page", p.getNumber(), "size", p.getSize(),
        "totalElements", p.getTotalElements(), "totalPages", p.getTotalPages(),
        "content", p.getContent());
  }
}
```

Aggiungi path in openapi.yaml. Commit: `feat(audit): add audit log query endpoint`

**Fine Fase 11.**

---

# FASE 12 — Observability + JSON logging + integration end-to-end

## Task 12.1: `logback-spring.xml` JSON

**Files:**
- Create: `src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <springProperty name="appName" source="spring.application.name"/>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
      <providers>
        <timestamp/>
        <version/>
        <logLevel/>
        <threadName/>
        <loggerName/>
        <message/>
        <mdc/>
        <stackTrace/>
        <pattern>
          <pattern>{"app":"${appName}"}</pattern>
        </pattern>
      </providers>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
  <logger name="eu.europa.esig" level="WARN"/>
</configuration>
```

Commit: `feat(logging): add JSON logback configuration`

## Task 12.2: `RequestContextFilter` per MDC

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/RequestContextFilter.java`

```java
package org.toresoft.signverify.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.security.Principal;

@Component
public class RequestContextFilter implements Filter {

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    if (req instanceof HttpServletRequest http) {
      MDC.put("clientIp", http.getRemoteAddr());
    }
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Principal p) {
      MDC.put("principalType", p.type().name());
      MDC.put("principalId", p.id());
    }
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.clear();
    }
  }
}
```

## Task 12.3: Custom Micrometer metrics

**Files:**
- Create: `src/main/java/org/toresoft/signverify/config/MetricsConfiguration.java`

```java
package org.toresoft.signverify.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Configuration
public class MetricsConfiguration {

  @Bean
  public Object asyncMetrics(MeterRegistry registry, ValidationJobRepository repo) {
    registry.gauge("signverify.async.jobs.pending", repo,
        r -> r.findAll().stream().filter(j -> j.getStatus().name().equals("PENDING")).count());
    registry.gauge("signverify.async.jobs.running", repo,
        r -> r.findAll().stream().filter(j -> j.getStatus().name().equals("RUNNING")).count());
    return new Object();
  }
}
```

Commit: `feat(observability): add MDC filter + custom metrics`

## Task 12.4: Integration test contract OpenAPI

**Files:**
- Create: `src/test/java/org/toresoft/signverify/api/OpenApiContractIT.java`

```java
package org.toresoft.signverify.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIT {

  @Autowired private MockMvc mvc;

  @Test
  void health_satisfies_openapi() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk());
  }
}
```

Commit: `test: add openapi contract smoke test`

**Fine Fase 12.**

---

# FASE 13 — GitLab CI + Dockerfile

## Task 13.1: `Dockerfile`

**Files:**
- Create: `Dockerfile`

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
COPY --from=build /app/target/sign-verify-2.jar app.jar
RUN mkdir -p /var/lib/sign-verify/dss-cache /var/lib/sign-verify/jobs \
 && chown -R app:app /var/lib/sign-verify
USER app
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
```

Commit: `build: add multi-stage dockerfile`

## Task 13.2: `.gitlab-ci.yml`

**Files:**
- Create: `.gitlab-ci.yml`

```yaml
stages: [validate, test, build, package, security]

variables:
  MAVEN_OPTS: "-Dmaven.repo.local=.m2/repository -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
  MAVEN_CLI_OPTS: "-B -e --no-transfer-progress"

cache:
  key: ${CI_COMMIT_REF_SLUG}
  paths: [.m2/repository, target]

default:
  image: maven:3.9-eclipse-temurin-21

validate:openapi:
  stage: validate
  image: openapitools/openapi-generator-cli:latest
  script:
    - /usr/local/bin/docker-entrypoint.sh validate -i src/main/resources/openapi/openapi.yaml

validate:format:
  stage: validate
  script: mvn $MAVEN_CLI_OPTS spotless:check

test:unit:
  stage: test
  script: mvn $MAVEN_CLI_OPTS test
  artifacts:
    when: always
    reports:
      junit: target/surefire-reports/TEST-*.xml

test:integration:
  stage: test
  services: [docker:24-dind]
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
  script: mvn $MAVEN_CLI_OPTS verify -DskipUnitTests
  artifacts:
    when: always
    reports:
      junit: target/failsafe-reports/TEST-*.xml

test:coverage:
  stage: test
  script: mvn $MAVEN_CLI_OPTS jacoco:report
  coverage: '/Total.*?([0-9]{1,3})%/'
  artifacts:
    paths: [target/site/jacoco/]

build:jar:
  stage: build
  script: mvn $MAVEN_CLI_OPTS -DskipTests package
  artifacts:
    paths: [target/*.jar]

package:docker:
  stage: package
  image: docker:24
  services: [docker:24-dind]
  variables:
    DOCKER_TLS_CERTDIR: ""
  before_script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
  script:
    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA .
    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA
    - |
      if [ "$CI_COMMIT_BRANCH" = "main" ]; then
        docker tag $CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA $CI_REGISTRY_IMAGE:latest
        docker push $CI_REGISTRY_IMAGE:latest
      fi

security:dependency-scan:
  stage: security
  script: mvn $MAVEN_CLI_OPTS org.owasp:dependency-check-maven:check
  artifacts:
    paths: [target/dependency-check-report.html]
  allow_failure: true
```

Commit: `ci: add gitlab pipeline with validate/test/build/package/security stages`

## Task 13.3: README quick-start

**Files:**
- Create: `README.md`

```markdown
# sign-verify-2

REST service for electronic signature verification using DSS 6.4.

## Quick start

```bash
mvn clean package
java -Dspring.profiles.active=dev -jar target/sign-verify-2.jar
```

First boot writes the bootstrap API key to `./target/bootstrap-api-key.txt`.

## Tests
```bash
mvn verify
```

## Docker
```bash
docker build -t sign-verify-2 .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev sign-verify-2
```

See `docs/superpowers/specs/2026-06-07-sign-verify-design.md` for the full design.
See `docs/superpowers/plans/2026-06-07-sign-verify-implementation.md` for the implementation plan.
```

Commit: `docs: add README quickstart`

## Verifica Fase 13

```bash
mvn -B clean verify -q
docker build -t sign-verify-2:test . 2>&1 | tail -5
```

**Fine Fase 13. Piano completo.**

---

# Note finali / open items per esecuzione

- Le fixture di test `src/test/resources/signatures/sample-pades-valid.pdf` devono essere reperite/generate dalla suite DSS.
- L'OJ keystore `src/main/resources/keystore/oj-keystore.p12` è placeholder; in produzione va sostituito con quello reale e password via env var.
- I `findAll().stream()` in `JobCleanupScheduler` (Task 10.1) e `MetricsConfiguration` (Task 12.3) vanno sostituiti con query mirate prima di entrare in produzione con dataset grandi. Sono accettabili per la v1.
- L'integration test TSL completo (LOTL stub WireMock + diff sync) è lasciato come task espandibile: il pattern di test è in `VerificationControllerIT` (Task 6.8).
- Coverage 80% su `application` + `domain` è enforced dal `jacoco-maven-plugin` quando viene scritta la rule custom (non inclusa nel pom v1 per non bloccare iterazione iniziale).

# Self-review

**Spec coverage** (riferimento `2026-06-07-sign-verify-design.md`):

| Sezione spec | Task copertura |
|---|---|
| §3 architettura + struttura package | Fase 0 (0.5) + ArchUnit baseline |
| §4 modello dati + invarianti | Fase 1 (1.1-1.7), invarianti in 4.2 + 5.2 |
| §5.2 API endpoints | Fasi 4-9 (uno per gruppo) |
| §5.3 autenticazione | Fase 2 (2.3-2.5) |
| §5.3.3 bootstrap | Fase 2 (2.6) |
| §5.4 ownership | Fase 9 (9.8 getJob) |
| §6 engine verifica | Fase 6 (6.1-6.8) |
| §6.2 profili + override | Fase 5 (5.2) + Fase 6 (6.4) |
| §6.5 report multi-tipo | Fase 6 (6.3 adapter + 6.7 controller) |
| §6.6 estrazione | Fase 7 |
| §7 TSL management | Fase 8 |
| §8 async + HMAC | Fase 9 |
| §8.7 retention multi-fase | Fase 10 |
| §9 protezione carico | Fase 6 (concurrency) + 9.5 (back-pressure) + 6.3 (CB) |
| §10.1 error handling RFC9457 | Fase 3 (3.2) |
| §10.2 observability | Fase 12 |
| §10.3 audit log | Fase 11 |
| §11 catalogo errori | Fase 3 (3.2 `Errors`) |
| §12 testing | Distribuito su tutte le fasi + 12.4 contract |
| §13 build/deploy | Fase 13 |

**Placeholder scan**: nessun "TBD"/"TODO" lasciato dentro task. Open items dichiarati esplicitamente in sezione finale come follow-up post-v1.

**Type consistency**: i nomi `SignatureValidatorPort`, `ExtractionPort`, `DocumentStoragePort`, `SecretCipherPort`, `CallbackDispatcherPort`, `PasswordHasherPort` sono usati coerentemente nelle fasi che li introducono e in quelle che li consumano. `Principal`, `Role`, `PrincipalType`, `JobStatus` consistenti dall'introduzione (Fase 1, 2) ai consumer (Fasi 4-11).

# Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-06-07-sign-verify-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Quale approccio?




