# Design Document: Sentry Integration (Optional)

## 1. Problem Statement

The sign-verify-2 service currently has structured JSON logging (logstash-logback-encoder) and Prometheus metrics, but no centralized error tracking. Uncaught exceptions and ERROR-level log events are only visible in container logs, making production debugging reactive and slow. Sentry provides automatic error grouping, alerting, and contextual breadcrumbs — but must be **optional** (zero overhead when disabled) and **non-intrusive** (no domain-layer coupling, no test interference).

## 2. Proposed Approach

Add Sentry as a **drop-in optional dependency** using the official Spring Boot 3.x Jakarta starter. The integration is purely additive:

- **Empty DSN = no-op**: Sentry SDK initializes but sends nothing. This is the recommended disable mechanism and avoids the incomplete overhead of `sentry.enabled=false`.
- **Dual capture path**: `SentryExceptionResolver` (HIGHEST_PRECEDENCE) captures exceptions from the Spring MVC chain with rich request context; `SentryAppender` in logback captures ERROR-level log events from non-MVC code paths (async workers, scheduled tasks, startup failures).
- **Breadcrumbs**: INFO-level log events become Sentry breadcrumbs, providing a timeline leading up to errors.
- **No Java configuration class needed**: The starter auto-configures everything via YAML properties. The logback XML handles appender-level filtering.

### Tradeoffs

| Decision | Rationale |
|---|---|
| Empty DSN over `sentry.enabled=false` | `enabled=false` does not prevent all instrumentation overhead per Sentry docs |
| HIGHEST_PRECEDENCE exception resolver | Captures exceptions BEFORE `GlobalExceptionHandler` handles them, ensuring Sentry sees all errors even those with specific `@ExceptionHandler` methods |
| Manual SentryAppender in logback-spring.xml | Custom logback config bypasses Spring Boot's auto-appender registration |
| No `SentryConfiguration.java` | All behavior achievable via YAML + logback XML; avoids unnecessary code |
| Accept potential duplicate events | Sentry server-side deduplication merges events with the same exception fingerprint |

## 3. Files/Modules to Change

### 3.1 `pom.xml`

**Location**: `<dependencyManagement>` section (after line 55, alongside existing BOMs)

**Add** Sentry BOM:
```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-bom</artifactId>
    <version>${sentry.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

**Location**: `<properties>` section (after line 37)

**Add** version property:
```xml
<sentry.version>8.16.0</sentry.version>
```

**Location**: `<dependencies>` section, inside the existing `<!-- Observability -->` block (after line 228, after logstash-logback-encoder)

**Add** two dependencies:
```xml
<!-- Sentry (optional — empty DSN = no-op) -->
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
</dependency>
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-logback</artifactId>
</dependency>
```

**Rationale**: The BOM manages versions for both artifacts. The starter provides auto-configuration for Spring Boot 3.x / Jakarta. `sentry-logback` provides the `SentryAppender` for logback integration. Versions are managed by the BOM so no explicit version on the dependencies.

---

### 3.2 `src/main/resources/application.yaml`

**Location**: After the `logging:` block (after line 126), add a new top-level `sentry:` block.

**Add**:
```yaml
sentry:
  dsn: ${SENTRY_DSN:}
  environment: ${SENTRY_ENVIRONMENT:development}
  release: ${SENTRY_RELEASE:unknown}
  send-default-pii: false
  exception-resolver-order: -2147483647
```

**Configuration rationale**:

| Property | Value | Rationale |
|---|---|---|
| `dsn` | `${SENTRY_DSN:}` | Empty default = no-op. Operator sets `SENTRY_DSN` env var to enable. |
| `environment` | `${SENTRY_ENVIRONMENT:development}` | Tags events with deployment environment. Defaults to `development` for local runs. |
| `release` | `${SENTRY_RELEASE:unknown}` | Tags events with release version. CI can inject git SHA at deploy time. |
| `send-default-pii` | `false` | GDPR compliance: prevents Sentry from capturing IP addresses, user cookies, and other PII automatically. |
| `exception-resolver-order` | `-2147483647` | `HIGHEST_PRECEDENCE` — Sentry captures the exception before `GlobalExceptionHandler` processes it. The resolver returns `null` (does not produce a response), so the normal exception handling chain continues. |

---

### 3.3 `src/test/resources/application-test.yaml`

**Location**: After the existing `app:` block (after line 64), add a new top-level `sentry:` block.

**Add**:
```yaml
sentry:
  dsn: ""
```

**Rationale**: Explicitly sets an empty DSN for the test profile. The Sentry SDK initializes in no-op mode — no network calls, no event buffering, no background threads sending data. This is more robust than `sentry.enabled=false` which does not prevent all instrumentation overhead.

---

### 3.4 `src/main/resources/logback-spring.xml`

**Location**: Inside the `<configuration>` element.

**Add** SentryAppender (after the STDOUT appender, before the `<root>` element):
```xml
<appender name="SENTRY" class="io.sentry.logback.SentryAppender">
    <!-- Only ERROR+ log events create Sentry issues -->
    <minimumEventLevel>ERROR</minimumEventLevel>
    <!-- INFO+ log events become Sentry breadcrumbs for context -->
    <minimumBreadcrumbLevel>INFO</minimumBreadcrumbLevel>
</appender>
```

**Modify** the `<root>` element to include the Sentry appender:
```xml
<root level="INFO">
    <appender-ref ref="STDOUT"/>
    <appender-ref ref="SENTRY"/>
</root>
```

**Full resulting logback-spring.xml**:
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
  <appender name="SENTRY" class="io.sentry.logback.SentryAppender">
    <minimumEventLevel>ERROR</minimumEventLevel>
    <minimumBreadcrumbLevel>INFO</minimumBreadcrumbLevel>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
    <appender-ref ref="SENTRY"/>
  </root>
  <logger name="eu.europa.esig" level="WARN"/>
</configuration>
```

**Rationale**: The custom logback-spring.xml bypasses Spring Boot's auto-registration of the SentryAppender. Manual configuration is required. The `minimumEventLevel=ERROR` ensures only ERROR-level log events create Sentry issues (matching requirement 6). The `minimumBreadcrumbLevel=INFO` ensures INFO-level events become breadcrumbs for context (matching requirement 7). When DSN is empty, the SentryAppender is a no-op — no overhead.

---

### 3.5 `docker-compose.prod.yml`

**Location**: Inside `services.app.environment` (after line 20, after `APP_OJ_KEYSTORE_PASSWORD`)

**Add**:
```yaml
      # --- Observability (optional) ---
      SENTRY_DSN: ${SENTRY_DSN:-}
      SENTRY_ENVIRONMENT: ${SENTRY_ENVIRONMENT:-production}
      SENTRY_RELEASE: ${SENTRY_RELEASE:-}
```

**Rationale**: Uses Docker Compose `${VAR:-}` syntax (empty default) so the variables are always passed through but default to empty. When `SENTRY_DSN` is empty/unset, Sentry is a no-op. `SENTRY_ENVIRONMENT` defaults to `production` for the prod compose file. `SENTRY_RELEASE` is optional — operators can inject a git SHA or version tag.

---

### 3.6 `.env.example`

**Location**: After the last entry (after line 17)

**Add**:
```
# Sentry error tracking (optional — leave empty to disable)
SENTRY_DSN=
SENTRY_ENVIRONMENT=production
SENTRY_RELEASE=
```

**Rationale**: Documents the new environment variables. Empty `SENTRY_DSN` makes it clear the feature is opt-in.

---

### 3.7 Files NOT Changed

| File | Reason |
|---|---|
| `GlobalExceptionHandler.java` | No changes needed. Sentry's exception resolver captures before the handler runs. The existing `log.error("Uncaught exception", ex)` in `handleGeneric()` still fires and creates a Sentry event via the logback appender — Sentry deduplicates server-side. |
| `docker-compose.yml` (dev) | Dev compose doesn't pass Sentry vars. Empty DSN default = no-op. Developers can optionally set `SENTRY_DSN` in their shell to test locally. |
| `application-dev.yaml` | No Sentry overrides needed. The base `application.yaml` default (`${SENTRY_DSN:}`) already produces an empty DSN. |
| `application-docker.yaml` | Same as dev — empty DSN default is sufficient. |
| `Dockerfile` | No changes. Sentry dependencies are bundled in the fat jar like any other dependency. |
| Domain layer (`domain/` package) | ArchUnit compliance: no Sentry imports in domain. All Sentry config is in YAML + logback XML (no Java code at all). |
| `ArchitectureTest.java` | Existing rules already prevent Spring imports in domain. No new Sentry-specific rule needed since we add zero Java code. |

## 4. Interfaces and Contracts

### 4.1 Environment Variables (Operator Contract)

| Variable | Required | Default | Description |
|---|---|---|---|
| `SENTRY_DSN` | No | `""` (empty) | Sentry project DSN. Empty = disabled. |
| `SENTRY_ENVIRONMENT` | No | `development` | Deployment environment tag (e.g., `production`, `staging`). |
| `SENTRY_RELEASE` | No | `unknown` | Release version tag (e.g., git SHA). |

### 4.2 Sentry Event Flow

```
Exception in Spring MVC handler chain:
  → SentryExceptionResolver (HIGHEST_PRECEDENCE) captures exception → Sentry event
  → GlobalExceptionHandler handles exception → RFC 7807 response
  → If handleGeneric() logs at ERROR → SentryAppender captures → Sentry deduplicates

Exception in async worker / scheduled task / startup:
  → Caught and logged at ERROR by application code
  → SentryAppender captures ERROR log event → Sentry event

INFO-level log event (any code path):
  → SentryAppender records as breadcrumb (no Sentry event created)
  → Breadcrumbs attached to next ERROR event as context timeline
```

### 4.3 No New Java Interfaces

This integration adds zero Java classes. All behavior is configured via:
- YAML properties (read by Sentry Spring Boot starter auto-configuration)
- Logback XML (SentryAppender configuration)
- Environment variables (operator-controlled at deploy time)

## 5. Invariants and Constraints

| # | Invariant | How Enforced |
|---|---|---|
| I1 | **Sentry sends nothing when DSN is empty** | Sentry SDK no-op mode with empty DSN. No network calls, no event buffering. |
| I2 | **No Sentry imports in `domain` package** | No Java code added at all. ArchUnit `domain_does_not_depend_on_spring` rule still passes (Sentry is `io.sentry.*`, not Spring, but we add zero imports anywhere in domain). |
| I3 | **Tests run without Sentry interference** | `application-test.yaml` sets `sentry.dsn: ""`. SDK initializes in no-op mode. No background threads, no network calls, no flaky test risk. |
| I4 | **GDPR: no PII sent to Sentry** | `send-default-pii: false` prevents automatic IP, cookie, and header capture. |
| I5 | **Only ERROR-level events create Sentry issues** | `SentryAppender.minimumEventLevel=ERROR` in logback XML. `SentryExceptionResolver` captures unhandled exceptions (which are inherently error-level). |
| I6 | **INFO-level events become breadcrumbs** | `SentryAppender.minimumBreadcrumbLevel=INFO` in logback XML. |
| I7 | **Existing GlobalExceptionHandler behavior unchanged** | No modifications to `GlobalExceptionHandler.java`. Sentry's exception resolver returns `null` (does not produce a response), so the normal handler chain continues. |
| I8 | **Existing structured JSON logging unchanged** | STDOUT appender in logback-spring.xml is untouched. SentryAppender is additive. |

## 6. Failure Modes

| Failure Mode | Impact | Mitigation |
|---|---|---|
| **Invalid DSN format** | Sentry SDK logs a warning at startup, does not send events. Application starts normally. | Operator validates DSN before deploying. Sentry SDK is resilient to malformed DSNs. |
| **Sentry service unreachable** | Events are buffered in memory (default: 100 events), then dropped. No application impact. No blocking, no timeouts on request path. | Sentry SDK uses async transport. Network failures are isolated from application threads. |
| **Sentry rate limiting (HTTP 429)** | SDK backs off automatically. Events are dropped during backoff. | No operator action needed. SDK handles rate limiting internally. |
| **Duplicate events (resolver + logback)** | Same exception captured twice: once by `SentryExceptionResolver`, once by `SentryAppender` (via `log.error()` in `handleGeneric()`). | Sentry server-side deduplication merges events with the same exception fingerprint. The duplicate is cosmetic, not functional. |
| **Increased memory usage** | Sentry SDK allocates a small buffer for event queue and breadcrumb ring buffer. | Negligible (~1-2 MB). Within existing 1GB Docker memory limit. |
| **Startup time increase** | Sentry SDK initialization adds ~50-100ms to startup. | Acceptable given 120s health check start_period. |
| **Dependency conflict** | Sentry BOM could conflict with Spring Boot managed dependencies. | Sentry BOM is scoped to `io.sentry:*` artifacts only. No overlap with Spring Boot or DSS BOMs. |

## 7. Migration Strategy

**No migration needed.** This is a purely additive change:

1. Merge the PR — Sentry dependencies are bundled in the image.
2. Deploy — Sentry initializes in no-op mode (empty DSN default).
3. To enable: set `SENTRY_DSN` environment variable and restart.
4. To disable: unset `SENTRY_DSN` and restart.

No database changes, no API changes, no configuration file format changes.

## 8. Verification Strategy

### 8.1 Build Verification

```bash
# Compile and run all tests (unit + integration)
mvn clean verify

# Verify ArchUnit rules still pass (domain isolation)
mvn test -Dtest=ArchitectureTest

# Verify Spotless formatting
mvn spotless:check
```

**Expected**: All tests pass. ArchUnit rules pass. No formatting violations.

### 8.2 No-Op Verification (DSN Empty)

```bash
# Start without SENTRY_DSN
docker compose -f docker-compose.prod.yml up -d

# Check startup logs — should see Sentry SDK init with no DSN
docker compose -f docker-compose.prod.yml logs app | grep -i sentry
```

**Expected**: Sentry SDK logs "No DSN set, Sentry is disabled" (or similar). No network calls to Sentry. Application functions normally.

### 8.3 Active Verification (DSN Set)

```bash
# Start with a valid SENTRY_DSN
SENTRY_DSN=https://examplePublicKey@o0.ingest.sentry.io/0 docker compose -f docker-compose.prod.yml up -d

# Trigger an unhandled exception (e.g., request with invalid auth)
curl -H "Authorization: Bearer invalid-token" http://localhost:8080/api/v1/...

# Check Sentry dashboard
```

**Expected**: New issue appears in Sentry with:
- Exception type and stack trace
- Environment tag (`production`)
- Release tag (if set)
- Breadcrumbs from preceding INFO-level log events
- No PII (no IP address, no cookies)

### 8.4 Test Isolation Verification

```bash
# Run tests and verify no Sentry network calls
mvn test -Dspring.profiles.active=test

# Check test output for Sentry-related warnings
mvn test 2>&1 | grep -i sentry
```

**Expected**: No Sentry-related network calls. No Sentry warnings. All tests pass.

### 8.5 Logback Verification

```bash
# Trigger an ERROR-level log event
# Check that STDOUT still produces JSON structured logs
# Check that Sentry event is created (if DSN set) or not (if DSN empty)
```

**Expected**: JSON structured logs on STDOUT unchanged. Sentry events only when DSN is set.

## 9. Review Checklist

- [ ] `pom.xml`: Sentry BOM in `<dependencyManagement>`, version property in `<properties>`, two dependencies in Observability section
- [ ] `application.yaml`: `sentry:` block with env-var interpolation, `send-default-pii: false`, `exception-resolver-order: -2147483647`
- [ ] `application-test.yaml`: `sentry.dsn: ""` explicitly set
- [ ] `logback-spring.xml`: SentryAppender with `minimumEventLevel=ERROR`, `minimumBreadcrumbLevel=INFO`, added to `<root>`
- [ ] `docker-compose.prod.yml`: `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_RELEASE` env vars
- [ ] `.env.example`: `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_RELEASE` documented
- [ ] No Java code added (zero new classes)
- [ ] No changes to `GlobalExceptionHandler.java`
- [ ] No changes to domain layer
- [ ] ArchUnit tests still pass
- [ ] All existing tests still pass
- [ ] Spotless formatting passes

## 10. Summary of Changes

| File | Change Type | Lines Added | Lines Modified |
|---|---|---|---|
| `pom.xml` | Add BOM + 2 dependencies | ~15 | 0 |
| `application.yaml` | Add `sentry:` block | ~6 | 0 |
| `application-test.yaml` | Add `sentry:` block | ~2 | 0 |
| `logback-spring.xml` | Add SentryAppender + root ref | ~5 | 1 |
| `docker-compose.prod.yml` | Add 3 env vars | ~4 | 0 |
| `.env.example` | Add 3 env var docs | ~4 | 0 |
| **Total** | | **~36** | **1** |
