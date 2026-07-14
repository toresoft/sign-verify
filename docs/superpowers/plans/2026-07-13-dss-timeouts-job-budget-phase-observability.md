# DSS Timeouts, Per-Job Budget, Phase Observability — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound and make configurable the DSS network calls, enforce a deterministic per-job wall-clock budget, and persist the current validation phase so a stuck job is diagnosable — without changing eIDAS verification semantics.

**Architecture:** Three orthogonal additions on the async validation path. (1) A single timeout-configured `CommonsDataLoader` is injected into the OCSP/CRL/AIA sources and the TSL loader. (2) A per-instance in-memory deadline map plus a `@Scheduled` watchdog marks over-budget RUNNING jobs `FAILED job.timeout` through a conditional bulk update (multi-instance-safe: an instance only times out its own jobs). (3) A persisted `phase` column, updated with a version-neutral bulk update at each phase boundary, is exposed via `GET /jobs/{id}` and mirrored to structured logs and Micrometer timers. All DB writes reuse the conditional / version-neutral bulk-update idiom introduced in commit `c71ba5d`.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA (JPQL bulk updates), Flyway, DSS 6.4 (`eu.europa.esig.dss.service.http.commons.CommonsDataLoader`), Micrometer/Prometheus, resilience4j, JUnit 5 + Mockito + AssertJ, `@DataJpaTest` on Testcontainers Postgres.

## Global Constraints

- Java 21; Spring Boot 4.1.0; Maven (no wrapper). If `mvn` is not on `PATH`: `source ~/.sdkman/bin/sdkman-init.sh`.
- Jackson 3 (`tools.jackson`); the `JsonMapper` type is `tools.jackson.databind.json.JsonMapper`.
- Hexagonal architecture enforced by ArchUnit (`ArchitectureTest`); controllers map to DTOs, never expose entities.
- DB schema owned by **Flyway** (`src/main/resources/db/migration`); Hibernate is `ddl-auto: validate`. Add a `V__*.sql` migration; never let Hibernate alter the schema.
- Timestamp columns are `TIMESTAMP` (without time zone), matching the existing schema.
- Errors extend/produce `AppException` → emitted as `application/problem+json` (RFC 9457).
- Run `mvn spotless:apply` (Google Java Format) before every commit. Commit messages in English; **no `Co-Authored-By` trailer**.
- All source files start with the project LGPL license header (copy verbatim from any existing file in the same package).
- Config decisions fixed with the maintainer: budget over-run ⇒ `FAILED job.timeout`, **no retry**; watchdog is per-instance in-memory (not a DB sweep); verification semantics unchanged (unreachable revocation still degrades to INDETERMINATE with the job COMPLETED).

---

## File Structure

**Feature 1 — DSS timeouts**
- Create `src/main/java/org/toresoft/signverify/config/DssHttpProperties.java` — `@ConfigurationProperties("app.dss.http")` holding the four `Duration` timeouts.
- Modify `src/main/java/org/toresoft/signverify/config/DssConfiguration.java` — new `revocationDataLoader` bean; inject it into the three online sources and the TSL loader.
- Modify `src/main/resources/application.yaml` — `app.dss.http.*` defaults.
- Create `src/test/java/org/toresoft/signverify/config/DssTimeoutConfigTest.java`.

**Feature 2 — Per-job budget**
- Modify `src/main/java/org/toresoft/signverify/domain/exception/Errors.java` — `JOB_TIMEOUT`.
- Modify `src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java` — `markTimedOut`.
- Modify `src/main/java/org/toresoft/signverify/application/ValidationWorker.java` — deadline map, `enforceBudgets` watchdog, config, startup assertion, timeout counter.
- Modify `src/main/resources/application.yaml` — `app.async.worker.max-validation-time`, `budget-check-interval`.
- Modify `src/test/java/org/toresoft/signverify/persistence/ValidationJobRepositoryTest.java` — `markTimedOut` tests.
- Modify `src/test/java/org/toresoft/signverify/application/ValidationWorkerTest.java` — constructor args, watchdog tests, startup assertion.

**Feature 3 — Phase observability**
- Create `src/main/java/org/toresoft/signverify/domain/model/ValidationPhase.java`.
- Create `src/main/resources/db/migration/V7__validation_job_phase.sql`.
- Modify `src/main/java/org/toresoft/signverify/domain/model/ValidationJob.java` — `phase`, `phaseUpdatedAt` fields + accessors.
- Modify `src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java` — `setPhase`.
- Modify `src/main/java/org/toresoft/signverify/application/ValidationWorker.java` — `setPhase` calls, per-phase timers, completion log.
- Modify `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java` — expose `phase`/`phaseUpdatedAt`.
- Modify `src/main/resources/openapi/openapi.yaml` — `JobView.phase`, `JobView.phaseUpdatedAt`.
- Modify `src/test/java/org/toresoft/signverify/persistence/ValidationJobConcurrencyTest.java` — `setPhase` tests.
- Modify `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java` — phase in response.

---

## Task 1: Explicit DSS HTTP timeouts

**Files:**
- Create: `src/main/java/org/toresoft/signverify/config/DssHttpProperties.java`
- Modify: `src/main/java/org/toresoft/signverify/config/DssConfiguration.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/org/toresoft/signverify/config/DssTimeoutConfigTest.java`

**Interfaces:**
- Produces: `DssHttpProperties` with `Duration getConnectTimeout()`, `getConnectionRequestTimeout()`, `getResponseTimeout()`, `getSocketTimeout()`; bean `CommonsDataLoader revocationDataLoader(DssHttpProperties)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/toresoft/signverify/config/DssTimeoutConfigTest.java` (copy the LGPL header from `DssConfiguration.java`):

```java
package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DssTimeoutConfigTest {

  @Test
  void revocationDataLoader_appliesConfiguredTimeouts() {
    DssHttpProperties props = new DssHttpProperties();
    props.setConnectTimeout(Duration.ofSeconds(3));
    props.setConnectionRequestTimeout(Duration.ofSeconds(4));
    props.setResponseTimeout(Duration.ofSeconds(10));
    props.setSocketTimeout(Duration.ofSeconds(11));

    CommonsDataLoader loader = new DssConfiguration().revocationDataLoader(props);

    assertThat(loader.getTimeoutConnection()).isEqualTo(3000);
    assertThat(loader.getTimeoutConnectionRequest()).isEqualTo(4000);
    assertThat(loader.getTimeoutResponse()).isEqualTo(10000);
    assertThat(loader.getTimeoutSocket()).isEqualTo(11000);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=DssTimeoutConfigTest test`
Expected: compile failure — `DssHttpProperties` and `revocationDataLoader` do not exist yet.

- [ ] **Step 3: Create `DssHttpProperties`**

Create `src/main/java/org/toresoft/signverify/config/DssHttpProperties.java` (LGPL header, then):

```java
package org.toresoft.signverify.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeouts applied to every DSS HTTP fetch (OCSP/CRL/AIA revocation and TSL retrieval). */
@ConfigurationProperties("app.dss.http")
public class DssHttpProperties {

  /** TCP connect timeout. */
  private Duration connectTimeout = Duration.ofSeconds(3);

  /** Time to lease a connection from the pool. */
  private Duration connectionRequestTimeout = Duration.ofSeconds(3);

  /** Time to receive the response. */
  private Duration responseTimeout = Duration.ofSeconds(10);

  /** Socket inactivity (read) timeout. */
  private Duration socketTimeout = Duration.ofSeconds(10);

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
    this.connectionRequestTimeout = connectionRequestTimeout;
  }

  public Duration getResponseTimeout() {
    return responseTimeout;
  }

  public void setResponseTimeout(Duration responseTimeout) {
    this.responseTimeout = responseTimeout;
  }

  public Duration getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(Duration socketTimeout) {
    this.socketTimeout = socketTimeout;
  }
}
```

- [ ] **Step 4: Wire the loader in `DssConfiguration`**

In `src/main/java/org/toresoft/signverify/config/DssConfiguration.java`:

Add to the class-level annotation the new properties type:

```java
@EnableConfigurationProperties({TslProperties.class, ExtractionProperties.class, DssHttpProperties.class})
```

Add a helper + bean (place `toMillis` and `revocationDataLoader` near `fileCacheDataLoader`):

```java
  /** Clamps a Duration to a positive int millisecond value for the DSS loader setters. */
  private static int toMillis(java.time.Duration d) {
    long ms = d.toMillis();
    if (ms <= 0) throw new IllegalArgumentException("DSS http timeout must be positive: " + d);
    return (int) Math.min(ms, Integer.MAX_VALUE);
  }

  private static CommonsDataLoader timeoutBoundedLoader(DssHttpProperties props) {
    CommonsDataLoader loader = new CommonsDataLoader();
    loader.setTimeoutConnection(toMillis(props.getConnectTimeout()));
    loader.setTimeoutConnectionRequest(toMillis(props.getConnectionRequestTimeout()));
    loader.setTimeoutResponse(toMillis(props.getResponseTimeout()));
    loader.setTimeoutSocket(toMillis(props.getSocketTimeout()));
    return loader;
  }

  @Bean
  public CommonsDataLoader revocationDataLoader(DssHttpProperties props) {
    return timeoutBoundedLoader(props);
  }
```

Change `certificateVerifier` to accept and inject the loader:

```java
  @Bean
  public CertificateVerifier certificateVerifier(
      TrustedListsCertificateSource tsl, CommonsDataLoader revocationDataLoader) {
    CommonCertificateVerifier cv = new CommonCertificateVerifier();
    cv.setTrustedCertSources(tsl);
    cv.setAIASource(new DefaultAIASource(revocationDataLoader));
    cv.setOcspSource(new OnlineOCSPSource(revocationDataLoader));
    cv.setCrlSource(new OnlineCRLSource(revocationDataLoader));
    cv.setRevocationFallback(true);
    return cv;
  }
```

Change `fileCacheDataLoader` to reuse the timeout-bounded loader instead of a default one. Replace its `loader.setDataLoader(new CommonsDataLoader());` line and add the `DssHttpProperties` parameter:

```java
  @Bean
  public FileCacheDataLoader fileCacheDataLoader(
      @Value("${app.dss.cache-dir}") String cacheDir, DssHttpProperties props) throws IOException {
    Path dir = Path.of(cacheDir);
    Files.createDirectories(dir);
    FileCacheDataLoader loader = new FileCacheDataLoader();
    loader.setDataLoader(timeoutBoundedLoader(props));
    loader.setFileCacheDirectory(dir.toFile());
    loader.setCacheExpirationTime(0); // refresh sempre online quando chiamato
    return loader;
  }
```

- [ ] **Step 5: Add config defaults to `application.yaml`**

Under `app.dss:` (which currently holds `cache-dir` and `online-mode`), add:

```yaml
  dss:
    cache-dir: ${APP_DSS_CACHE_DIR:/var/lib/sign-verify/dss-cache}
    online-mode: true
    http:
      connect-timeout: 3s
      connection-request-timeout: 3s
      response-timeout: 10s
      socket-timeout: 10s
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=DssTimeoutConfigTest test`
Expected: PASS.

- [ ] **Step 7: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/config/DssHttpProperties.java \
        src/main/java/org/toresoft/signverify/config/DssConfiguration.java \
        src/main/resources/application.yaml \
        src/test/java/org/toresoft/signverify/config/DssTimeoutConfigTest.java
git commit -m "feat(dss): explicit, configurable HTTP timeouts on revocation and TSL loaders"
```

---

## Task 2: `JOB_TIMEOUT` error code + `markTimedOut` repository method

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/domain/exception/Errors.java`
- Modify: `src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java`
- Test: `src/test/java/org/toresoft/signverify/persistence/ValidationJobRepositoryTest.java`

**Interfaces:**
- Consumes: `JobStatus.RUNNING/FAILED`, `Errors.JOB_TIMEOUT`.
- Produces: `Errors.JOB_TIMEOUT = "job.timeout"`; `int markTimedOut(UUID id, Instant now)` — 1 if the job was RUNNING and is now FAILED, else 0.

- [ ] **Step 1: Write the failing tests**

In `ValidationJobRepositoryTest.java` add (the file already has a `newJob(JobStatus, String)` helper and imports `Errors`, `JobStatus`, `Instant`):

```java
  @Test
  void markTimedOut_failsRunningJob_andArmsCallback() {
    ValidationJob running = newJob(JobStatus.RUNNING, "alice");
    running.setCallbackUrl("https://callback.example/hook");
    repo.save(running);
    em.flush();

    int n = repo.markTimedOut(running.getId(), Instant.now());

    assertThat(n).isEqualTo(1);
    ValidationJob j = repo.findById(running.getId()).orElseThrow();
    assertThat(j.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(j.getErrorCode()).isEqualTo(Errors.JOB_TIMEOUT);
    assertThat(j.getErrorMessage()).isEqualTo("validation exceeded time budget");
    assertThat(j.getCompletedAt()).isNotNull();
    assertThat(j.getNextCallbackAt()).isNotNull();
  }

  @Test
  void markTimedOut_isNoOp_whenJobNotRunning() {
    ValidationJob completed = newJob(JobStatus.COMPLETED, "alice");
    repo.save(completed);
    em.flush();

    assertThat(repo.markTimedOut(completed.getId(), Instant.now())).isZero();
    assertThat(repo.findById(completed.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.COMPLETED);
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationJobRepositoryTest test`
Expected: compile failure — `markTimedOut` / `Errors.JOB_TIMEOUT` not defined.

- [ ] **Step 3: Add the error code**

In `Errors.java`, add after `JOB_EXPIRED`:

```java
  public static final String JOB_TIMEOUT = "job.timeout";
```

- [ ] **Step 4: Add the repository method**

In `ValidationJobRepository.java`, add after `claimForProcessing` (mirrors `expireOverdue`):

```java
  /**
   * Marks a single RUNNING job FAILED with {@code job.timeout} when it exceeds its wall-clock
   * budget. Conditional on {@code status = RUNNING} and version-bumped explicitly (bulk JPQL
   * bypasses {@code @Version}), so a job that finished, was requeued, or expired in the meantime
   * yields 0 rows instead of clobbering the new state. The first callback is armed when a callback
   * URL is configured.
   *
   * @return number of rows updated (1 = timed out, 0 = no longer RUNNING)
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.status = org.toresoft.signverify.domain.model.JobStatus.FAILED,
             j.errorCode = 'job.timeout',
             j.errorMessage = 'validation exceeded time budget',
             j.completedAt = :now,
             j.nextCallbackAt = CASE WHEN j.callbackUrl IS NOT NULL THEN :now ELSE j.nextCallbackAt END,
             j.version = j.version + 1
       WHERE j.id = :id
         AND j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING
      """)
  int markTimedOut(@Param("id") UUID id, @Param("now") Instant now);
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationJobRepositoryTest test`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/domain/exception/Errors.java \
        src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java \
        src/test/java/org/toresoft/signverify/persistence/ValidationJobRepositoryTest.java
git commit -m "feat(async): add job.timeout error code and markTimedOut conditional update"
```

---

## Task 3: Per-instance budget watchdog in `ValidationWorker`

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/application/ValidationWorker.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/org/toresoft/signverify/application/ValidationWorkerTest.java`

**Interfaces:**
- Consumes: `repo.markTimedOut(UUID, Instant)`, `MeterRegistry`.
- Produces: `void enforceBudgets()`; deadline registration inside the executor lambda; constructor now also takes `@Value("${app.async.worker.max-validation-time}") Duration maxValidationTime` and `MeterRegistry meterRegistry`; throws `IllegalStateException` at construction if `maxValidationTime >= staleRunningTimeout`.

- [ ] **Step 1: Write the failing tests**

In `ValidationWorkerTest.java`:

Add imports:

```java
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
```

Add a helper to build a worker with explicit budgets (place near `setUp`), and record the registry as a field:

```java
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private ValidationWorker workerWithBudget(Duration maxValidationTime, Duration staleRunning) {
    // Executor that runs submitted tasks inline so process() completes within the test.
    Executor inline = Runnable::run;
    return new ValidationWorker(
        repo, storage, profileService, applier, validator, om, registry,
        MAX_PICKUP_ATTEMPTS, staleRunning, maxValidationTime, inline, WORKERS, meterRegistry);
  }

  @Test
  void constructor_rejectsBudgetNotSmallerThanStaleTimeout() {
    assertThatThrownBy(() -> workerWithBudget(Duration.ofMinutes(30), Duration.ofMinutes(30)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void enforceBudgets_timesOutPastDeadlineJobs_incrementsCounter_andForgetsThem() {
    ValidationWorker w = workerWithBudget(Duration.ofMinutes(5), Duration.ofMinutes(30));
    UUID overdue = UUID.randomUUID();
    // Register a deadline already in the past for this instance's job.
    w.registerDeadlineForTest(overdue, Instant.now().minusSeconds(1));
    when(repo.markTimedOut(eq(overdue), any())).thenReturn(1);

    w.enforceBudgets();

    verify(repo).markTimedOut(eq(overdue), any());
    assertThat(meterRegistry.counter("signverify.validation.timeout").count()).isEqualTo(1.0);
    // Second run must not re-time-out the same job (entry was forgotten).
    w.enforceBudgets();
    verify(repo, times(1)).markTimedOut(eq(overdue), any());
  }

  @Test
  void enforceBudgets_ignoresJobsWithinDeadline() {
    ValidationWorker w = workerWithBudget(Duration.ofMinutes(5), Duration.ofMinutes(30));
    w.registerDeadlineForTest(UUID.randomUUID(), Instant.now().plusSeconds(300));

    w.enforceBudgets();

    verify(repo, never()).markTimedOut(any(), any());
  }
```

Add `import static org.mockito.ArgumentMatchers.eq;` and `import static org.mockito.Mockito.times;` if not already present (the file already imports `eq`, `times`, `never`, `verify`).

- [ ] **Step 2: Run tests to verify they fail**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationWorkerTest test`
Expected: compile failure — new constructor arity, `enforceBudgets`, and `registerDeadlineForTest` do not exist.

- [ ] **Step 3: Extend the worker constructor and fields**

In `ValidationWorker.java` add imports:

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
```

Add fields:

```java
  private final Duration maxValidationTime;
  private final MeterRegistry meterRegistry;
  // Per-instance deadlines for jobs THIS instance is validating. A DB sweep cannot enforce the
  // budget safely in a multi-instance deployment (it cannot tell "slow but alive on instance B"
  // from "crashed on instance B"); each instance times out only its own in-flight jobs. Crash of
  // this instance drops these deadlines, and the (longer) stale-RUNNING sweep requeues the job.
  private final ConcurrentHashMap<UUID, Instant> deadlines = new ConcurrentHashMap<>();
```

Change the constructor signature to add the two parameters (insert `maxValidationTime` right after `staleRunningTimeout`, and `meterRegistry` at the end):

```java
  public ValidationWorker(
      ValidationJobRepository repo,
      DocumentStoragePort storage,
      VerificationProfileService profileService,
      PolicyOverrideApplier overrideApplier,
      SignatureValidatorPort validator,
      JsonMapper om,
      CircuitBreakerRegistry circuitRegistry,
      @Value("${app.async.max-pickup-attempts}") int maxPickupAttempts,
      @Value("${app.async.worker.stale-running-timeout}") Duration staleRunningTimeout,
      @Value("${app.async.worker.max-validation-time}") Duration maxValidationTime,
      @Qualifier("validationExecutor") Executor validationExecutor,
      @Value("${app.async.workers}") int workers,
      MeterRegistry meterRegistry) {
    // The watchdog must fire before the stale-RUNNING sweep, or a slow-but-alive job would be
    // requeued (crash-recovery path) instead of failing deterministically with job.timeout.
    if (maxValidationTime.compareTo(staleRunningTimeout) >= 0) {
      throw new IllegalStateException(
          "app.async.worker.max-validation-time ("
              + maxValidationTime
              + ") must be < stale-running-timeout ("
              + staleRunningTimeout
              + ")");
    }
    this.repo = repo;
    this.storage = storage;
    this.profileService = profileService;
    this.overrideApplier = overrideApplier;
    this.validator = validator;
    this.om = om;
    this.circuitRegistry = circuitRegistry;
    this.maxPickupAttempts = maxPickupAttempts;
    this.staleRunningTimeout = staleRunningTimeout;
    this.maxValidationTime = maxValidationTime;
    this.validationExecutor = validationExecutor;
    this.capacity = new Semaphore(workers);
    this.meterRegistry = meterRegistry;
  }
```

- [ ] **Step 4: Register/forget deadlines around `process` in `poll()`**

In `poll()`, replace the executor lambda (the `validationExecutor.execute(...)` block) with one that registers the deadline before running and forgets it afterwards:

```java
        validationExecutor.execute(
            () -> {
              deadlines.put(id, Instant.now().plus(maxValidationTime));
              try {
                process(id);
              } catch (Exception e) {
                log.error("worker error on job {}", id, e);
              } finally {
                deadlines.remove(id);
                capacity.release();
              }
            });
```

- [ ] **Step 5: Add the watchdog and the test seam**

Add these methods to `ValidationWorker` (place after `poll()`):

```java
  /**
   * Times out jobs THIS instance has been validating past their budget. Runs on a short fixed
   * delay. Each over-deadline job is failed with job.timeout via a conditional bulk update (no-op
   * if it already finished) and then forgotten so it is not timed out twice. The orphaned
   * validation thread keeps draining (bounded by the DSS HTTP timeouts) and its eventual
   * markCompleted/markFailed no-ops because the job is no longer RUNNING.
   */
  @Scheduled(fixedDelayString = "${app.async.worker.budget-check-interval}")
  public void enforceBudgets() {
    Instant now = Instant.now();
    for (Map.Entry<UUID, Instant> e : deadlines.entrySet()) {
      if (now.isAfter(e.getValue())) {
        UUID id = e.getKey();
        deadlines.remove(id);
        if (repo.markTimedOut(id, now) == 1) {
          meterRegistry.counter("signverify.validation.timeout").increment();
          log.warn("job {} timed out: exceeded validation budget of {}", id, maxValidationTime);
        }
      }
    }
  }

  // Test seam: register an in-flight deadline without going through the executor. Package-private
  // on purpose; not part of the production API.
  void registerDeadlineForTest(UUID id, Instant deadline) {
    deadlines.put(id, deadline);
  }
```

(`Map` is already imported via `java.util.*`.)

- [ ] **Step 6: Add config defaults**

In `application.yaml`, under `app.async.worker:` (which currently has `poll-interval` and `stale-running-timeout`), add:

```yaml
    worker:
      poll-interval: 5s
      # RUNNING jobs whose claim is older than this are assumed stranded by a crashed instance
      # and requeued. Must exceed the longest legitimate validation by a wide margin.
      stale-running-timeout: 30m
      # Hard wall-clock budget for a single validation. On overrun the job is FAILED job.timeout
      # (no retry). MUST be < stale-running-timeout so the watchdog fires before the stale sweep.
      max-validation-time: 5m
      # How often each instance checks its own in-flight jobs against their deadlines.
      budget-check-interval: 15s
```

- [ ] **Step 7: Update the existing `setUp` constructor call**

In `ValidationWorkerTest.setUp()` the current call is:

```java
    worker =
        new ValidationWorker(
            repo, storage, profileService, applier, validator, om, registry,
            MAX_PICKUP_ATTEMPTS,
            java.time.Duration.ofMinutes(30),
            Runnable::run,
            WORKERS);
```

Add the budget (after the 30m stale timeout) and the meter registry (last), matching the constructor order:

```java
    worker =
        new ValidationWorker(
            repo, storage, profileService, applier, validator, om, registry,
            MAX_PICKUP_ATTEMPTS,
            java.time.Duration.ofMinutes(30),
            java.time.Duration.ofMinutes(5),
            Runnable::run,
            WORKERS,
            meterRegistry);
```

The `meterRegistry` field is added in Step 1 (`private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();`), so it is initialized before `setUp` runs.

- [ ] **Step 8: Run tests to verify they pass**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationWorkerTest test`
Expected: PASS.

- [ ] **Step 9: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/application/ValidationWorker.java \
        src/main/resources/application.yaml \
        src/test/java/org/toresoft/signverify/application/ValidationWorkerTest.java
git commit -m "feat(async): per-instance wall-clock budget watchdog with deterministic job.timeout"
```

---

## Task 4: `ValidationPhase` enum, migration, and entity fields

**Files:**
- Create: `src/main/java/org/toresoft/signverify/domain/model/ValidationPhase.java`
- Create: `src/main/resources/db/migration/V7__validation_job_phase.sql`
- Modify: `src/main/java/org/toresoft/signverify/domain/model/ValidationJob.java`
- Test: `src/test/java/org/toresoft/signverify/persistence/ValidationJobRepositoryTest.java`

**Interfaces:**
- Produces: `enum ValidationPhase { STORAGE_READ, POLICY_RESOLVE, DSS_VALIDATE, RESULT_STORE }`; `ValidationJob.getPhase()/setPhase(ValidationPhase)`, `getPhaseUpdatedAt()/setPhaseUpdatedAt(Instant)`.

- [ ] **Step 1: Write the failing test**

In `ValidationJobRepositoryTest.java` add (imports `ValidationPhase` — add `import org.toresoft.signverify.domain.model.ValidationPhase;`):

```java
  @Test
  void phase_isPersistedAndReadBack() {
    ValidationJob j = newJob(JobStatus.RUNNING, "alice");
    j.setPhase(ValidationPhase.DSS_VALIDATE);
    j.setPhaseUpdatedAt(Instant.now());
    repo.save(j);
    em.flush();
    em.clear();

    ValidationJob read = repo.findById(j.getId()).orElseThrow();
    assertThat(read.getPhase()).isEqualTo(ValidationPhase.DSS_VALIDATE);
    assertThat(read.getPhaseUpdatedAt()).isNotNull();
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationJobRepositoryTest#phase_isPersistedAndReadBack test`
Expected: compile failure — `ValidationPhase` and the accessors do not exist.

- [ ] **Step 3: Create the enum**

Create `src/main/java/org/toresoft/signverify/domain/model/ValidationPhase.java` (LGPL header, then):

```java
package org.toresoft.signverify.domain.model;

/** Phase a RUNNING validation job is currently in, for live diagnosis of a stuck job. */
public enum ValidationPhase {
  STORAGE_READ,
  POLICY_RESOLVE,
  DSS_VALIDATE,
  RESULT_STORE
}
```

- [ ] **Step 4: Create the Flyway migration**

Create `src/main/resources/db/migration/V7__validation_job_phase.sql`:

```sql
-- Current validation phase of a RUNNING job, so a stuck in-flight job is diagnosable via
-- GET /jobs/{id} and directly in the DB without grepping logs. Both columns are nullable and
-- populated only while the job is RUNNING.
ALTER TABLE validation_job ADD COLUMN phase VARCHAR(20);
ALTER TABLE validation_job ADD COLUMN phase_updated_at TIMESTAMP NULL;
```

- [ ] **Step 5: Add the entity fields**

In `ValidationJob.java`, add the fields near `lastAccessedAt` (follow the existing `@Enumerated`/`@Column` style):

```java
  @Enumerated(EnumType.STRING)
  @Column(name = "phase", length = 20)
  private ValidationPhase phase;

  @Column(name = "phase_updated_at")
  private Instant phaseUpdatedAt;
```

Add the accessors near the other getters/setters:

```java
  public ValidationPhase getPhase() {
    return phase;
  }

  public void setPhase(ValidationPhase phase) {
    this.phase = phase;
  }

  public Instant getPhaseUpdatedAt() {
    return phaseUpdatedAt;
  }

  public void setPhaseUpdatedAt(Instant phaseUpdatedAt) {
    this.phaseUpdatedAt = phaseUpdatedAt;
  }
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationJobRepositoryTest#phase_isPersistedAndReadBack test`
Expected: PASS (Flyway applies V7, Hibernate `validate` accepts the new columns).

- [ ] **Step 7: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/domain/model/ValidationPhase.java \
        src/main/resources/db/migration/V7__validation_job_phase.sql \
        src/main/java/org/toresoft/signverify/domain/model/ValidationJob.java \
        src/test/java/org/toresoft/signverify/persistence/ValidationJobRepositoryTest.java
git commit -m "feat(async): persist validation phase on the job (schema + entity)"
```

---

## Task 5: `setPhase` version-neutral repository method

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java`
- Test: `src/test/java/org/toresoft/signverify/persistence/ValidationJobConcurrencyTest.java`

**Interfaces:**
- Consumes: `ValidationPhase`, `JobStatus.RUNNING`.
- Produces: `int setPhase(UUID id, ValidationPhase phase, Instant now)` — version-neutral, conditional on `status = RUNNING`; 1 if updated, 0 otherwise.

- [ ] **Step 1: Write the failing tests**

In `ValidationJobConcurrencyTest.java` add (add `import org.toresoft.signverify.domain.model.ValidationPhase;`):

```java
  @Test
  void setPhase_updatesRunningJob_withoutBumpingVersion() {
    ValidationJob seed = newRunningJob();
    repo.save(seed);
    em.flush();
    em.clear();

    // A copy loaded before the phase update (as the worker holds) must stay valid: setPhase must
    // not bump @Version, so a later conditional finalize still lands.
    ValidationJob workerCopy = repo.findById(seed.getId()).orElseThrow();
    em.detach(workerCopy);

    assertThat(repo.setPhase(seed.getId(), ValidationPhase.DSS_VALIDATE, Instant.now())).isEqualTo(1);
    em.clear();

    ValidationJob read = repo.findById(seed.getId()).orElseThrow();
    assertThat(read.getPhase()).isEqualTo(ValidationPhase.DSS_VALIDATE);
    assertThat(repo.markCompleted(workerCopy.getId(), "result.json", Instant.now())).isEqualTo(1);
  }

  @Test
  void setPhase_isNoOp_whenJobNotRunning() {
    ValidationJob seed = newRunningJob();
    seed.setStatus(JobStatus.COMPLETED);
    repo.save(seed);
    em.flush();
    em.clear();

    assertThat(repo.setPhase(seed.getId(), ValidationPhase.DSS_VALIDATE, Instant.now())).isZero();
    assertThat(repo.findById(seed.getId()).orElseThrow().getPhase()).isNull();
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationJobConcurrencyTest test`
Expected: compile failure — `setPhase` not defined.

- [ ] **Step 3: Add the repository method**

In `ValidationJobRepository.java` add `import org.toresoft.signverify.domain.model.ValidationPhase;` and, after `touchLastAccessed`:

```java
  /**
   * Stamps the current validation {@code phase} with a version-neutral bulk UPDATE (same idiom as
   * {@link #touchLastAccessed}): it must not bump {@code @Version} or it would invalidate the
   * worker's in-flight outcome write. Conditional on {@code status = RUNNING} so a draining
   * orphan cannot write a phase onto a job the watchdog already failed.
   *
   * @return number of rows updated (1 = stamped, 0 = no longer RUNNING)
   */
  @Modifying
  @Transactional
  @Query(
      """
      UPDATE ValidationJob j
         SET j.phase = :phase, j.phaseUpdatedAt = :now
       WHERE j.id = :id
         AND j.status = org.toresoft.signverify.domain.model.JobStatus.RUNNING
      """)
  int setPhase(
      @Param("id") UUID id, @Param("phase") ValidationPhase phase, @Param("now") Instant now);
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationJobConcurrencyTest test`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java \
        src/test/java/org/toresoft/signverify/persistence/ValidationJobConcurrencyTest.java
git commit -m "feat(async): version-neutral setPhase conditional update"
```

---

## Task 6: Phase instrumentation in `ValidationWorker`

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/application/ValidationWorker.java`
- Test: `src/test/java/org/toresoft/signverify/application/ValidationWorkerTest.java`

**Interfaces:**
- Consumes: `repo.setPhase(UUID, ValidationPhase, Instant)`, `ValidationPhase`, `MeterRegistry`.
- Produces: `process()` stamps `STORAGE_READ → POLICY_RESOLVE → DSS_VALIDATE → RESULT_STORE` and records a `signverify.validation.phase{phase=…}` Timer per phase.

- [ ] **Step 1: Write the failing test**

In `ValidationWorkerTest.java` add these imports: `import org.toresoft.signverify.domain.model.ValidationPhase;`, `import org.mockito.InOrder;`, `import static org.mockito.Mockito.inOrder;`. `setUp()` already stubs `repo.claimForProcessing(...)` leniently to 1, and `stubHappyPath()` stubs storage/profile/validator/storeResult/markCompleted:

```java
  @Test
  void process_stampsPhasesInOrder() {
    ValidationJob job = newPendingJob();
    when(repo.findById(job.getId())).thenReturn(Optional.of(job));
    stubHappyPath();

    worker.process(job.getId());

    InOrder ordered = inOrder(repo);
    ordered.verify(repo).setPhase(eq(job.getId()), eq(ValidationPhase.STORAGE_READ), any());
    ordered.verify(repo).setPhase(eq(job.getId()), eq(ValidationPhase.POLICY_RESOLVE), any());
    ordered.verify(repo).setPhase(eq(job.getId()), eq(ValidationPhase.DSS_VALIDATE), any());
    ordered.verify(repo).setPhase(eq(job.getId()), eq(ValidationPhase.RESULT_STORE), any());
  }
```

`repo.setPhase(...)` is left unstubbed — Mockito returns 0 by default, which is fine since the worker ignores the return value.

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationWorkerTest#process_stampsPhasesInOrder test`
Expected: FAIL — `setPhase` is never called (no interaction).

- [ ] **Step 3: Instrument `process()`**

Add a private helper to `ValidationWorker`:

```java
  private void enterPhase(UUID id, ValidationPhase phase) {
    repo.setPhase(id, phase, Instant.now());
  }

  private void recordPhase(ValidationPhase phase, long startNanos) {
    meterRegistry
        .timer("signverify.validation.phase", "phase", phase.name())
        .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
  }
```

Rewrite the `try` block of `process()` to stamp and time each phase (replace the body from `byte[] file = ...` down to the `markCompleted` block):

```java
    try {
      long t0 = System.nanoTime();
      enterPhase(id, ValidationPhase.STORAGE_READ);
      byte[] file = storage.read(job.getDocumentPath());
      recordPhase(ValidationPhase.STORAGE_READ, t0);

      long t1 = System.nanoTime();
      enterPhase(id, ValidationPhase.POLICY_RESOLVE);
      var profile = profileService.getOrDefault(job.getProfileId());
      String policy = profile.getPolicyXml();
      if (job.getProfileOverrides() != null && !job.getProfileOverrides().isBlank()) {
        Map<String, Object> ov = om.readValue(job.getProfileOverrides(), new TypeReference<>() {});
        policy = overrideApplier.apply(policy, ov);
      }
      Set<ReportType> reports = parseReports(job.getReportsRequested());
      recordPhase(ValidationPhase.POLICY_RESOLVE, t1);

      long t2 = System.nanoTime();
      enterPhase(id, ValidationPhase.DSS_VALIDATE);
      log.info("job {} entering DSS validation", id);
      var result =
          validator.validate(
              new ValidationRequest(file, job.getDocumentFilename(), policy, reports));
      recordPhase(ValidationPhase.DSS_VALIDATE, t2);

      long t3 = System.nanoTime();
      enterPhase(id, ValidationPhase.RESULT_STORE);
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
      String resultPath =
          storage.storeResult(job.getId().toString(), om.writeValueAsBytes(resultJson));
      recordPhase(ValidationPhase.RESULT_STORE, t3);

      // Finalize via a conditional bulk UPDATE (status=RUNNING), not save() on the stale entity.
      int updated = repo.markCompleted(id, resultPath, Instant.now());
      if (updated == 0) {
        log.info("job {} result dropped: no longer RUNNING at finalize", id);
      } else {
        log.info("job {} completed", id);
      }
    } catch (AppException ae) {
```

(The `catch` arms below stay exactly as they are.)

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationWorkerTest#process_stampsPhasesInOrder test`
Expected: PASS.

- [ ] **Step 5: Run the full worker test class (regression)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=ValidationWorkerTest test`
Expected: PASS (the phase stamping does not change any finalize behavior the existing tests assert).

- [ ] **Step 6: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/application/ValidationWorker.java \
        src/test/java/org/toresoft/signverify/application/ValidationWorkerTest.java
git commit -m "feat(async): stamp and time validation phases in the worker"
```

---

## Task 7: Expose `phase` in the job status response

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java`
- Modify: `src/main/resources/openapi/openapi.yaml`
- Test: `src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java`

**Interfaces:**
- Consumes: `ValidationJob.getPhase()/getPhaseUpdatedAt()`.
- Produces: `phase` and `phaseUpdatedAt` fields in the `GET /jobs/{id}` response body.

- [ ] **Step 1: Write the failing test**

In `AsyncVerificationControllerTest.java` add `import org.toresoft.signverify.domain.model.ValidationPhase;` and this test, modeled on the existing `getJob_returnsJobStatus` (uses the `baseJob(UUID, Principal)` helper, the `owner` principal field, `authFor`, `jobRepository`, and `mockMvc` already in the file):

```java
  @Test
  void getJob_exposesPhaseForRunningJob() throws Exception {
    UUID jobId = UUID.randomUUID();
    ValidationJob job = baseJob(jobId, owner);
    job.setStatus(JobStatus.RUNNING);
    job.setStartedAt(Instant.parse("2024-01-01T00:01:00Z"));
    job.setPhase(ValidationPhase.DSS_VALIDATE);
    job.setPhaseUpdatedAt(Instant.parse("2024-01-01T00:02:00Z"));
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

    mockMvc
        .perform(
            get("/api/v1/verifications/jobs/{id}", jobId)
                .with(authentication(authFor(owner, Role.STANDARD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("DSS_VALIDATE"))
        .andExpect(jsonPath("$.phaseUpdatedAt").exists());
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=AsyncVerificationControllerTest#getJob_exposesPhaseForRunningJob test`
Expected: FAIL — `$.phase` does not exist in the response.

- [ ] **Step 3: Expose the fields in `buildResponse`**

In `AsyncVerificationController.buildResponse`, after the `expiresAt`/`callbackAttempts` puts and before the `result`/`errorCode` block, add:

```java
    if (job.getPhase() != null) out.put("phase", job.getPhase().name());
    if (job.getPhaseUpdatedAt() != null)
      out.put("phaseUpdatedAt", job.getPhaseUpdatedAt().atOffset(ZoneOffset.UTC));
```

- [ ] **Step 4: Document the fields in the OpenAPI `JobView` schema**

In `openapi.yaml`, inside `JobView.properties` (after `callbackAttempts`), add:

```yaml
        phase:
          type: string
          enum: [STORAGE_READ, POLICY_RESOLVE, DSS_VALIDATE, RESULT_STORE]
          nullable: true
          description: >
            Current validation phase while the job is RUNNING; lets callers see where an
            in-flight job is spending time. Null once the job is terminal.
        phaseUpdatedAt:
          type: string
          format: date-time
          nullable: true
          description: When the current phase was entered.
```

- [ ] **Step 5: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=AsyncVerificationControllerTest#getJob_exposesPhaseForRunningJob test`
Expected: PASS.

- [ ] **Step 6: Run the OpenAPI contract IT (regression)**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q -Dtest=OpenApiContractIT test`
Expected: PASS (the added fields are valid schema; no existing response violates it).

- [ ] **Step 7: Format and commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add src/main/java/org/toresoft/signverify/api/AsyncVerificationController.java \
        src/main/resources/openapi/openapi.yaml \
        src/test/java/org/toresoft/signverify/api/AsyncVerificationControllerTest.java
git commit -m "feat(api): expose validation phase in the job status response"
```

---

## Task 8: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Full build with Failsafe IT, Spotless check, JaCoCo**

Run: `source ~/.sdkman/bin/sdkman-init.sh && mvn -q verify`
Expected: BUILD SUCCESS. Pay attention to:
- `DssRevocationNetworkIT` still green under the new default timeouts.
- `ArchitectureTest` green (no hexagonal boundary broken — `MeterRegistry` and DSS loaders live in adapter/config, not domain).
- `OpenApiContractIT` green.

- [ ] **Step 2: If any IT fails, triage before proceeding**

Do not paper over a failure by loosening a timeout or an assertion. If `DssRevocationNetworkIT` times out, confirm the default `app.dss.http.*` values are the intended ones and that the IT's expectations account for bounded revocation fetches; fix the root cause.

- [ ] **Step 3: Final commit if `spotless:apply` changed anything**

```bash
source ~/.sdkman/bin/sdkman-init.sh && mvn -q spotless:apply
git add -A && git commit -m "style: spotless formatting" || echo "nothing to format"
```

---

## Self-Review Notes

- **Spec coverage:** Section 1 → Task 1. Section 2 → Tasks 2 (error code + `markTimedOut`) and 3 (watchdog, deadlines, startup assertion, timeout counter, config). Section 3 → Tasks 4 (enum + schema + entity), 5 (`setPhase`), 6 (worker instrumentation + timers + logs), 7 (API response + OpenAPI). Config block → Tasks 1 and 3. Testing section → per-task tests + Task 8. The spec's "bonus" (timeout message naming the current phase) is intentionally dropped (YAGNI): the phase is already visible via the persisted column and `GET /jobs/{id}`.
- **Type consistency:** `markTimedOut(UUID, Instant)`, `setPhase(UUID, ValidationPhase, Instant)`, `ValidationPhase{STORAGE_READ,POLICY_RESOLVE,DSS_VALIDATE,RESULT_STORE}`, and the constructor parameter order (`… staleRunningTimeout, maxValidationTime, validationExecutor, workers, meterRegistry`) are used identically across tasks.
- **Multi-instance safety:** enforced by construction — deadlines live in per-instance memory; `enforceBudgets` only ever calls `markTimedOut` for ids this instance registered.
