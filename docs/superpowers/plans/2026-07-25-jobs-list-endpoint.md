# Jobs List Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /api/v1/verifications/jobs` — a paginated, filterable list of verification jobs (by owner, status, and creation date), returning a summary view scoped by the caller's role.

**Architecture:** Thin hand-rolled controller (mirroring `AuditController`) → `JobQueryService` (validation + role scoping + pagination) → `ValidationJobRepository` with `JpaSpecificationExecutor` + a `JobSpecifications` factory. Entities are mapped to a `JobSummaryDto` (never dumped raw) to avoid leaking sensitive columns (`callbackSecretCipher`, `documentPath`, …). Design-first: the OpenAPI contract is updated and DTOs regenerated.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JPA (`Specification`), JUnit 5, `@WebMvcTest` / `@DataJpaTest` (H2 for the test profile), Testcontainers Postgres for the end-to-end IT, Google Java Format via Spotless.

## Global Constraints

- Java 21; Spring Boot 4.1.0; Maven (no wrapper). Toolchain via SDKMAN: `source ~/.sdkman/bin/sdkman-init.sh` before `mvn`.
- Jackson 3 (`tools.jackson`): any `ObjectMapper` site uses `tools.jackson.databind.json.JsonMapper`.
- Hexagonal architecture, enforced by ArchUnit (`ArchitectureTest`): controllers in `api`, business logic in `application`, persistence in `persistence`, domain in `domain`. Controllers must not expose entities — map to DTOs.
- API is design-first: edit `src/main/resources/openapi/openapi.yaml`; generator produces `api.spi`/`api.dto`. `OpenApiContractIT` guards the contract (it validates the OpenAPI document is well-formed and that `/actuator/health` conforms).
- DB schema owned by Flyway (`src/main/resources/db/migration`); Hibernate is `ddl-auto: validate`. Any schema change is a new `V__*.sql` migration.
- Errors are thrown as `AppException` (`AppException.badRequest(String)`) → emitted as `application/problem+json` (RFC 9457) by `GlobalExceptionHandler`.
- Run `mvn spotless:apply` before every commit.
- Copyright header: every new `.java` file starts with the standard LGPL header block used across the codebase (copy verbatim from any existing source file, e.g. `Principal.java`).
- Commit messages in English; NO `Co-Authored-By` trailer.

## Reference facts (verified in the codebase)

- `ValidationJob` (entity, `domain.model`) stores `Instant createdAt` (NOT NULL), `startedAt`, `completedAt`, `expiresAt` (NOT NULL), `int callbackAttempts` (NOT NULL), `ValidationPhase phase` (nullable), `Instant phaseUpdatedAt` (nullable), `PrincipalType requestedByPrincipalType` (NOT NULL), `String requestedByPrincipalId` (NOT NULL), `JobStatus status` (NOT NULL), `String reportsRequested` (NOT NULL).
- `JobStatus` enum: `PENDING, RUNNING, COMPLETED, FAILED, DELIVERED, DELIVERY_FAILED, DELETED`.
- `ValidationPhase` enum: `STORAGE_READ, POLICY_RESOLVE, DSS_VALIDATE, RESULT_STORE`.
- `PrincipalType` enum: `API_KEY, OAUTH_USER, SYSTEM`.
- `Role` enum: `PRIVILEGED, STANDARD`.
- `Principal` record (`security`): `Principal(PrincipalType type, String id, Role role, String displayName)`.
- `ValidationJobRepository` currently: `interface ValidationJobRepository extends JpaRepository<ValidationJob, UUID>`.
- `AuditController` pattern: `Specification` + `PageRequest.of(page, size, Sort.by(DESC, field))`, envelope `{page, size, totalElements, totalPages, content}`.
- Security (`SecurityConfiguration`): `anyRequest().authenticated()` — no HTTP-layer role gate on `/api/v1/verifications/**`; role scoping is done in the service.
- `GlobalExceptionHandler` maps `MethodArgumentTypeMismatchException` → 400. So invalid `status`/`ownerType`/date/`page`/`size` query values already yield 400 at binding time; the service only needs cross-field validation.
- `buildResponse` in `AsyncVerificationController` serializes `requestedBy` as `{type, id}` and timestamps as `.atOffset(ZoneOffset.UTC)`. Match this JSON shape.
- Existing repo tests use `@DataJpaTest` + `@ActiveProfiles("test")` (H2) with `TestEntityManager`.

---

## Task 1: `JobSummaryDto` (summary DTO + mapping)

Immutable DTO exposing exactly the requested fields plus `requestedBy`. Mapping from the entity lives here (a pure function), so the query layers never touch presentation and never dump the entity. It lives in the `application` package (not `api`): the service and `JobPage` produce it, and the controller consumes it — keeping the dependency direction `api → application` (ArchUnit has no rule forbidding `application → api`, but the reverse direction is the clean one and avoids the smell).

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/JobSummaryDto.java`
- Test: `src/test/java/org/toresoft/signverify/application/JobSummaryDtoTest.java`

**Interfaces:**
- Consumes: `ValidationJob`, `PrincipalType`.
- Produces:
  - `record JobSummaryDto(UUID jobId, String status, JobSummaryDto.Owner requestedBy, OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt, OffsetDateTime expiresAt, int callbackAttempts, String phase, OffsetDateTime phaseUpdatedAt)`
  - nested `record Owner(String type, String id)`
  - `static JobSummaryDto from(ValidationJob job)`

- [ ] **Step 1: Write the failing test**

```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.model.ValidationPhase;

class JobSummaryDtoTest {

  private ValidationJob job(UUID id) {
    ValidationJob j = new ValidationJob();
    j.setId(id);
    j.setStatus(JobStatus.COMPLETED);
    j.setRequestedByPrincipalType(PrincipalType.OAUTH_USER);
    j.setRequestedByPrincipalId("user-123");
    j.setCreatedAt(Instant.parse("2026-07-14T22:13:24.272673Z"));
    j.setStartedAt(Instant.parse("2026-07-14T22:14:10.351944Z"));
    j.setCompletedAt(Instant.parse("2026-07-14T22:14:18.540216Z"));
    j.setExpiresAt(Instant.parse("2026-07-21T22:13:24.272673Z"));
    j.setCallbackAttempts(0);
    j.setPhase(ValidationPhase.RESULT_STORE);
    j.setPhaseUpdatedAt(Instant.parse("2026-07-14T22:14:18.530670Z"));
    return j;
  }

  @Test
  void from_mapsAllRequestedFields() {
    UUID id = UUID.randomUUID();
    JobSummaryDto dto = JobSummaryDto.from(job(id));

    assertThat(dto.jobId()).isEqualTo(id);
    assertThat(dto.status()).isEqualTo("COMPLETED");
    assertThat(dto.requestedBy().type()).isEqualTo("OAUTH_USER");
    assertThat(dto.requestedBy().id()).isEqualTo("user-123");
    assertThat(dto.createdAt()).isEqualTo(Instant.parse("2026-07-14T22:13:24.272673Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.startedAt()).isEqualTo(Instant.parse("2026-07-14T22:14:10.351944Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.completedAt()).isEqualTo(Instant.parse("2026-07-14T22:14:18.540216Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.expiresAt()).isEqualTo(Instant.parse("2026-07-21T22:13:24.272673Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.callbackAttempts()).isEqualTo(0);
    assertThat(dto.phase()).isEqualTo("RESULT_STORE");
    assertThat(dto.phaseUpdatedAt()).isEqualTo(Instant.parse("2026-07-14T22:14:18.530670Z").atOffset(ZoneOffset.UTC));
  }

  @Test
  void from_leavesNullableTimestampsAndPhaseNull() {
    ValidationJob j = job(UUID.randomUUID());
    j.setStartedAt(null);
    j.setCompletedAt(null);
    j.setPhase(null);
    j.setPhaseUpdatedAt(null);

    JobSummaryDto dto = JobSummaryDto.from(j);

    assertThat(dto.startedAt()).isNull();
    assertThat(dto.completedAt()).isNull();
    assertThat(dto.phase()).isNull();
    assertThat(dto.phaseUpdatedAt()).isNull();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=JobSummaryDtoTest`
Expected: FAIL — `JobSummaryDto` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

```java
package org.toresoft.signverify.application;

// <LGPL header block above the package line — copy from Principal.java>

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.toresoft.signverify.domain.model.ValidationJob;

/** Summary view of a {@link ValidationJob} for the paginated jobs list. Never exposes result
 * bodies, callback secrets, or storage paths. */
public record JobSummaryDto(
    UUID jobId,
    String status,
    Owner requestedBy,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime expiresAt,
    int callbackAttempts,
    String phase,
    OffsetDateTime phaseUpdatedAt) {

  /** The principal that submitted the job (its owner). */
  public record Owner(String type, String id) {}

  public static JobSummaryDto from(ValidationJob j) {
    return new JobSummaryDto(
        j.getId(),
        j.getStatus().name(),
        new Owner(j.getRequestedByPrincipalType().name(), j.getRequestedByPrincipalId()),
        utc(j.getCreatedAt()),
        utc(j.getStartedAt()),
        utc(j.getCompletedAt()),
        utc(j.getExpiresAt()),
        j.getCallbackAttempts(),
        j.getPhase() == null ? null : j.getPhase().name(),
        utc(j.getPhaseUpdatedAt()));
  }

  private static OffsetDateTime utc(Instant t) {
    return t == null ? null : t.atOffset(ZoneOffset.UTC);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=JobSummaryDtoTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh; mvn spotless:apply
git add src/main/java/org/toresoft/signverify/application/JobSummaryDto.java \
        src/test/java/org/toresoft/signverify/application/JobSummaryDtoTest.java
git commit -m "feat: add JobSummaryDto for jobs list view"
```

---

## Task 2: Repository `JpaSpecificationExecutor` + `JobSpecifications`

Enable dynamic queries and provide null-safe `Specification` factories, verified against real persisted rows on H2.

**Files:**
- Modify: `src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java`
- Create: `src/main/java/org/toresoft/signverify/persistence/JobSpecifications.java`
- Test: `src/test/java/org/toresoft/signverify/persistence/JobSpecificationsTest.java`

**Interfaces:**
- Consumes: `ValidationJob`, `JobStatus`, `PrincipalType`.
- Produces (all `static`, return `org.springframework.data.jpa.domain.Specification<ValidationJob>`):
  - `ownedBy(PrincipalType type, String id)`
  - `ownerIdEquals(String id)` — no-op (matches all) when `id == null`
  - `ownerTypeEquals(PrincipalType type)` — no-op when `type == null`
  - `statusIn(Collection<JobStatus> statuses)` — no-op when null/empty
  - `createdAtGte(Instant from)` — no-op when null
  - `createdAtLt(Instant to)` — no-op when null
- `ValidationJobRepository` additionally `extends JpaSpecificationExecutor<ValidationJob>` (gains `Page<ValidationJob> findAll(Specification<ValidationJob>, Pageable)`).

- [ ] **Step 1: Write the failing test**

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

@DataJpaTest
@ActiveProfiles("test")
class JobSpecificationsTest {

  @Autowired private ValidationJobRepository repo;
  @Autowired private TestEntityManager em;

  private ValidationJob persist(PrincipalType type, String owner, JobStatus status, Instant createdAt) {
    ValidationJob j = new ValidationJob();
    j.setId(UUID.randomUUID());
    j.setStatus(status);
    j.setReportsRequested("simple");
    j.setRequestedByPrincipalType(type);
    j.setRequestedByPrincipalId(owner);
    j.setCreatedAt(createdAt);
    j.setExpiresAt(createdAt.plusSeconds(3600));
    j.setCallbackAttempts(0);
    return em.persist(j);
  }

  private static PageRequest firstPageByCreatedDesc() {
    return PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void ownedBy_matchesOnlyThatPrincipal() {
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T10:00:00Z"));
    persist(PrincipalType.API_KEY, "other", JobStatus.PENDING, Instant.parse("2026-07-14T10:00:00Z"));
    em.flush();

    var page = repo.findAll(JobSpecifications.ownedBy(PrincipalType.API_KEY, "me"), firstPageByCreatedDesc());

    assertThat(page.getContent()).extracting(ValidationJob::getRequestedByPrincipalId).containsExactly("me");
  }

  @Test
  void statusIn_filtersToGivenStatuses() {
    persist(PrincipalType.API_KEY, "me", JobStatus.COMPLETED, Instant.parse("2026-07-14T10:00:00Z"));
    persist(PrincipalType.API_KEY, "me", JobStatus.FAILED, Instant.parse("2026-07-14T11:00:00Z"));
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T12:00:00Z"));
    em.flush();

    var spec = JobSpecifications.statusIn(List.of(JobStatus.COMPLETED, JobStatus.FAILED));
    var page = repo.findAll(spec, firstPageByCreatedDesc());

    assertThat(page.getContent()).extracting(ValidationJob::getStatus)
        .containsExactlyInAnyOrder(JobStatus.COMPLETED, JobStatus.FAILED);
  }

  @Test
  void createdAtRange_isFromInclusiveToExclusive() {
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-13T23:59:59Z"));
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T00:00:00Z")); // from, inclusive
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T23:59:59Z"));
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-15T00:00:00Z")); // to, exclusive
    em.flush();

    var spec = JobSpecifications.createdAtGte(Instant.parse("2026-07-14T00:00:00Z"))
        .and(JobSpecifications.createdAtLt(Instant.parse("2026-07-15T00:00:00Z")));
    var page = repo.findAll(spec, firstPageByCreatedDesc());

    assertThat(page.getContent()).extracting(ValidationJob::getCreatedAt)
        .containsExactly(
            Instant.parse("2026-07-14T23:59:59Z"),
            Instant.parse("2026-07-14T00:00:00Z")); // DESC order
  }

  @Test
  void nullArgs_areNoOps() {
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T10:00:00Z"));
    em.flush();

    var spec = JobSpecifications.statusIn(null)
        .and(JobSpecifications.ownerIdEquals(null))
        .and(JobSpecifications.ownerTypeEquals(null))
        .and(JobSpecifications.createdAtGte(null))
        .and(JobSpecifications.createdAtLt(null));

    assertThat(repo.findAll(spec, firstPageByCreatedDesc()).getTotalElements()).isEqualTo(1);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=JobSpecificationsTest`
Expected: FAIL — `JobSpecifications` missing and `repo.findAll(Specification, Pageable)` unresolved (compilation error).

- [ ] **Step 3a: Add `JpaSpecificationExecutor` to the repository**

Modify `ValidationJobRepository.java` — add the import and extend the interface:

```java
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
```

```java
public interface ValidationJobRepository
    extends JpaRepository<ValidationJob, UUID>, JpaSpecificationExecutor<ValidationJob> {
```

(Leave all existing methods untouched.)

- [ ] **Step 3b: Create `JobSpecifications`**

```java
package org.toresoft.signverify.persistence;

// <LGPL header block above the package line>

import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

/** Null-safe {@link Specification} factories for querying {@link ValidationJob}. A factory given a
 * null/empty argument returns a no-op specification (matches everything), so callers can compose
 * optional filters with {@code .and(...)} without null checks. */
public final class JobSpecifications {

  private JobSpecifications() {}

  private static Specification<ValidationJob> all() {
    return (root, q, cb) -> cb.conjunction();
  }

  public static Specification<ValidationJob> ownedBy(PrincipalType type, String id) {
    return ownerTypeEquals(type).and(ownerIdEquals(id));
  }

  public static Specification<ValidationJob> ownerIdEquals(String id) {
    return id == null ? all() : (root, q, cb) -> cb.equal(root.get("requestedByPrincipalId"), id);
  }

  public static Specification<ValidationJob> ownerTypeEquals(PrincipalType type) {
    return type == null
        ? all()
        : (root, q, cb) -> cb.equal(root.get("requestedByPrincipalType"), type);
  }

  public static Specification<ValidationJob> statusIn(Collection<JobStatus> statuses) {
    return statuses == null || statuses.isEmpty()
        ? all()
        : (root, q, cb) -> root.get("status").in(statuses);
  }

  public static Specification<ValidationJob> createdAtGte(Instant from) {
    return from == null
        ? all()
        : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
  }

  public static Specification<ValidationJob> createdAtLt(Instant to) {
    return to == null ? all() : (root, q, cb) -> cb.lessThan(root.get("createdAt"), to);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=JobSpecificationsTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh; mvn spotless:apply
git add src/main/java/org/toresoft/signverify/persistence/ValidationJobRepository.java \
        src/main/java/org/toresoft/signverify/persistence/JobSpecifications.java \
        src/test/java/org/toresoft/signverify/persistence/JobSpecificationsTest.java
git commit -m "feat: add JpaSpecificationExecutor and JobSpecifications for job queries"
```

---

## Task 3: `JobQuery`, `JobPage`, and `JobQueryService`

The application-layer brain: cross-field validation, role scoping, `createdOn` expansion, pagination clamp, and orchestration of repo + mapping.

**Files:**
- Create: `src/main/java/org/toresoft/signverify/application/JobQuery.java`
- Create: `src/main/java/org/toresoft/signverify/application/JobPage.java`
- Create: `src/main/java/org/toresoft/signverify/application/JobQueryService.java`
- Test: `src/test/java/org/toresoft/signverify/application/JobQueryServiceTest.java`

**Interfaces:**
- Consumes: `ValidationJobRepository` (Task 2), `JobSpecifications` (Task 2), `JobSummaryDto` (Task 1), `Principal`, `JobStatus`, `PrincipalType`, `Role`, `AppException`.
- Produces:
  - `record JobQuery(String ownerId, PrincipalType ownerType, List<JobStatus> statuses, LocalDate createdOn, Instant createdFrom, Instant createdTo, int page, int size)`
  - `record JobPage(int page, int size, long totalElements, int totalPages, List<JobSummaryDto> content)`
  - `class JobQueryService` with `JobPage list(Principal actor, JobQuery query)`

- [ ] **Step 1: Write the failing test**

```java
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@ExtendWith(MockitoExtension.class)
class JobQueryServiceTest {

  @Mock private ValidationJobRepository repo;
  @InjectMocks private JobQueryService service;

  @Captor private ArgumentCaptor<Specification<org.toresoft.signverify.domain.model.ValidationJob>> specCaptor;
  @Captor private ArgumentCaptor<PageRequest> pageCaptor;

  private final Principal standard = new Principal(PrincipalType.API_KEY, "me", Role.STANDARD, "me");
  private final Principal privileged = new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  private JobQuery emptyQuery(int page, int size) {
    return new JobQuery(null, null, null, null, null, null, page, size);
  }

  private void stubEmptyPage() {
    when(repo.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
  }

  @Test
  void standard_cannotUseOwnerFilter() {
    JobQuery q = new JobQuery("someone-else", null, null, null, null, null, 0, 50);
    assertThatThrownBy(() -> service.list(standard, q))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("owner filter requires PRIVILEGED");
  }

  @Test
  void createdOn_isMutuallyExclusiveWithRange() {
    JobQuery q = new JobQuery(null, null, null, LocalDate.parse("2026-07-14"), Instant.parse("2026-07-14T00:00:00Z"), null, 0, 50);
    assertThatThrownBy(() -> service.list(standard, q))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("createdOn");
  }

  @Test
  void fromAfterTo_isRejected() {
    JobQuery q = new JobQuery(null, null, null, null, Instant.parse("2026-07-15T00:00:00Z"), Instant.parse("2026-07-14T00:00:00Z"), 0, 50);
    assertThatThrownBy(() -> service.list(standard, q)).isInstanceOf(AppException.class);
  }

  @Test
  void size_isClampedTo200_andPageFloored() {
    stubEmptyPage();
    service.list(standard, emptyQuery(-3, 9999));
    verify(repo).findAll(specCaptor.capture(), pageCaptor.capture());
    assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(0);
    assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(200);
    assertThat(pageCaptor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void size_nonPositiveFallsBackToDefault50() {
    stubEmptyPage();
    service.list(standard, emptyQuery(0, 0));
    verify(repo).findAll(any(Specification.class), pageCaptor.capture());
    assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  void privileged_withOwnerFilter_isAllowed() {
    stubEmptyPage();
    JobQuery q = new JobQuery("some-owner", PrincipalType.OAUTH_USER, List.of(JobStatus.COMPLETED), null, null, null, 0, 50);
    JobPage result = service.list(privileged, q);
    assertThat(result.page()).isEqualTo(0);
    assertThat(result.content()).isEmpty();
    verify(repo).findAll(any(Specification.class), any(PageRequest.class));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=JobQueryServiceTest`
Expected: FAIL — `JobQuery`/`JobPage`/`JobQueryService` missing (compilation error).

- [ ] **Step 3a: Create `JobQuery`**

```java
package org.toresoft.signverify.application;

// <LGPL header block>

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;

/** Normalized query parameters for the jobs list. {@code createdFrom}/{@code createdTo} are
 * instants (UTC); {@code createdOn} is a calendar day expanded to a UTC range by the service. */
public record JobQuery(
    String ownerId,
    PrincipalType ownerType,
    List<JobStatus> statuses,
    LocalDate createdOn,
    Instant createdFrom,
    Instant createdTo,
    int page,
    int size) {}
```

- [ ] **Step 3b: Create `JobPage`**

```java
package org.toresoft.signverify.application;

// <LGPL header block>

import java.util.List;

/** Paginated envelope for the jobs list, mirroring the /audit-log response shape. */
public record JobPage(
    int page, int size, long totalElements, int totalPages, List<JobSummaryDto> content) {}
```

(`JobSummaryDto` is in the same `application` package — no import needed.)

- [ ] **Step 3c: Create `JobQueryService`**

```java
package org.toresoft.signverify.application;

// <LGPL header block>

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.persistence.JobSpecifications;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

/** Lists verification jobs with role-based scoping, filtering, and pagination. */
@Service
public class JobQueryService {

  private static final int MAX_SIZE = 200;
  private static final int DEFAULT_SIZE = 50;
  private static final Sort CREATED_DESC = Sort.by(Sort.Direction.DESC, "createdAt");

  private final ValidationJobRepository repo;

  public JobQueryService(ValidationJobRepository repo) {
    this.repo = repo;
  }

  public JobPage list(Principal actor, JobQuery q) {
    boolean privileged = actor.role() == Role.PRIVILEGED;

    if (!privileged && (q.ownerId() != null || q.ownerType() != null)) {
      throw AppException.badRequest("owner filter requires PRIVILEGED");
    }
    if (q.createdOn() != null && (q.createdFrom() != null || q.createdTo() != null)) {
      throw AppException.badRequest("createdOn cannot be combined with createdFrom/createdTo");
    }
    if (q.createdFrom() != null && q.createdTo() != null && q.createdFrom().isAfter(q.createdTo())) {
      throw AppException.badRequest("createdFrom must not be after createdTo");
    }

    Instant from;
    Instant to;
    if (q.createdOn() != null) {
      from = q.createdOn().atStartOfDay(ZoneOffset.UTC).toInstant();
      to = q.createdOn().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    } else {
      from = q.createdFrom();
      to = q.createdTo();
    }

    Specification<ValidationJob> scope =
        privileged
            ? JobSpecifications.ownerIdEquals(q.ownerId())
                .and(JobSpecifications.ownerTypeEquals(q.ownerType()))
            : JobSpecifications.ownedBy(actor.type(), actor.id());

    Specification<ValidationJob> spec =
        scope
            .and(JobSpecifications.statusIn(q.statuses()))
            .and(JobSpecifications.createdAtGte(from))
            .and(JobSpecifications.createdAtLt(to));

    int page = Math.max(0, q.page());
    int size = q.size() <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, q.size());

    Page<ValidationJob> result = repo.findAll(spec, PageRequest.of(page, size, CREATED_DESC));
    List<JobSummaryDto> content = result.getContent().stream().map(JobSummaryDto::from).toList();
    return new JobPage(
        result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), content);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=JobQueryServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh; mvn spotless:apply
git add src/main/java/org/toresoft/signverify/application/JobQuery.java \
        src/main/java/org/toresoft/signverify/application/JobPage.java \
        src/main/java/org/toresoft/signverify/application/JobQueryService.java \
        src/test/java/org/toresoft/signverify/application/JobQueryServiceTest.java
git commit -m "feat: add JobQueryService with role scoping, filtering, pagination"
```

---

## Task 4: OpenAPI contract — path, `JobSummary`, `JobPage`

Design-first: document the endpoint and its typed schemas; regenerate DTOs; keep the contract IT green.

**Files:**
- Modify: `src/main/resources/openapi/openapi.yaml`
- Verified by: `src/test/java/org/toresoft/signverify/api/OpenApiContractIT.java` (existing)

**Interfaces:**
- Consumes: existing `PrincipalRef` schema (added earlier), `JobStatus` enum values, `Problem` responses.
- Produces: OpenAPI `JobSummary` + `JobPage` schemas and the `GET /api/v1/verifications/jobs` operation.

- [ ] **Step 1: Add the path operation**

Insert a new operation under `paths:` immediately BEFORE the existing `/api/v1/verifications/jobs/{jobId}:` entry (so the collection path precedes the item path):

```yaml
  /api/v1/verifications/jobs:
    get:
      tags: [Verifications]
      operationId: listJobs
      summary: List verification jobs (paginated, filterable)
      description: >-
        Returns a paginated, filterable summary view of verification jobs. STANDARD principals
        see only their own jobs; PRIVILEGED principals see all and may filter by owner. Never
        includes result bodies or error details (use GET /jobs/{jobId} for those).
      parameters:
        - { name: ownerId, in: query, required: false, schema: { type: string },
            description: "Filter by owner principal id. PRIVILEGED only; STANDARD supplying it gets 400." }
        - { name: ownerType, in: query, required: false,
            schema: { type: string, enum: [API_KEY, OAUTH_USER, SYSTEM] },
            description: "Disambiguates ownerId. PRIVILEGED only." }
        - name: status
          in: query
          required: false
          style: form
          explode: true
          schema:
            type: array
            items: { type: string, enum: [PENDING, RUNNING, COMPLETED, FAILED, DELIVERED, DELIVERY_FAILED, DELETED] }
          description: "Repeatable. Matches any of the given statuses (IN)."
        - { name: createdOn, in: query, required: false, schema: { type: string, format: date },
            description: "Exact UTC day. Mutually exclusive with createdFrom/createdTo (else 400)." }
        - { name: createdFrom, in: query, required: false, schema: { type: string, format: date-time },
            description: "Lower bound, inclusive (createdAt >= createdFrom)." }
        - { name: createdTo, in: query, required: false, schema: { type: string, format: date-time },
            description: "Upper bound, exclusive (createdAt < createdTo)." }
        - { name: page, in: query, required: false, schema: { type: integer, default: 0 } }
        - { name: size, in: query, required: false, schema: { type: integer, default: 50 },
            description: "Clamped to a maximum of 200." }
      responses:
        '200':
          description: ok
          content:
            application/json:
              schema: { $ref: '#/components/schemas/JobPage' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
```

- [ ] **Step 2: Add the schemas**

Append under `components: schemas:` after `DeletedJobView` (added earlier):

```yaml
    JobSummary:
      type: object
      description: Summary view of a verification job in list results. No result body or error details.
      properties:
        jobId: { type: string, format: uuid }
        status: { type: string, enum: [PENDING, RUNNING, COMPLETED, FAILED, DELIVERED, DELIVERY_FAILED, DELETED] }
        requestedBy: { $ref: '#/components/schemas/PrincipalRef' }
        createdAt: { type: string, format: date-time }
        startedAt: { type: string, format: date-time, nullable: true }
        completedAt: { type: string, format: date-time, nullable: true }
        expiresAt: { type: string, format: date-time }
        callbackAttempts: { type: integer }
        phase:
          type: string
          enum: [STORAGE_READ, POLICY_RESOLVE, DSS_VALIDATE, RESULT_STORE]
          nullable: true
        phaseUpdatedAt: { type: string, format: date-time, nullable: true }
      required: [jobId, status, requestedBy, createdAt, expiresAt, callbackAttempts]

    JobPage:
      type: object
      description: Paginated envelope of job summaries.
      properties:
        page: { type: integer }
        size: { type: integer }
        totalElements: { type: integer, format: int64 }
        totalPages: { type: integer }
        content:
          type: array
          items: { $ref: '#/components/schemas/JobSummary' }
      required: [page, size, totalElements, totalPages, content]
```

- [ ] **Step 3: Regenerate DTOs and validate the contract**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn spotless:apply test -Dtest=OpenApiContractIT`
Expected: generator runs (`Generating with dryRun=false`), `JobSummary`/`JobPage` DTO classes appear under `target/generated-sources`, and `OpenApiContractIT` PASSES (1 test). If the generator errors, the YAML is malformed — fix indentation/anchors and rerun.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/openapi/openapi.yaml
git commit -m "feat(openapi): document GET /verifications/jobs with JobSummary/JobPage"
```

---

## Task 5: `VerificationJobListController`

Thin web layer: bind query params (native Spring conversion → 400 on bad enum/date), read the `Principal`, delegate to the service, return the envelope.

**Files:**
- Create: `src/main/java/org/toresoft/signverify/api/VerificationJobListController.java`
- Test: `src/test/java/org/toresoft/signverify/api/VerificationJobListControllerTest.java`

**Interfaces:**
- Consumes: `JobQueryService`, `JobQuery`, `JobPage` (Task 3), `Principal`, `JobStatus`, `PrincipalType`.
- Produces: `GET /api/v1/verifications/jobs` returning `JobPage` (JSON).

- [ ] **Step 1: Write the failing test**

```java
package org.toresoft.signverify.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.JobPage;
import org.toresoft.signverify.application.JobQuery;
import org.toresoft.signverify.application.JobQueryService;
import org.toresoft.signverify.application.JobSummaryDto;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.security.Principal;

@WebMvcTest(controllers = VerificationJobListController.class)
class VerificationJobListControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private JobQueryService service;
  // Collaborators pulled in by the security/filter slice; mock to satisfy the context.
  @MockitoBean private org.toresoft.signverify.persistence.ApiKeyRepository apiKeyRepository;
  @MockitoBean private org.toresoft.signverify.domain.port.PasswordHasherPort passwordHasher;

  private final Principal standard = new Principal(PrincipalType.API_KEY, "me", Role.STANDARD, "me");
  private final Principal privileged = new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  private static Authentication authFor(Principal p) {
    return new UsernamePasswordAuthenticationToken(
        p, null, List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
  }

  @Test
  void returnsEnvelope() throws Exception {
    JobSummaryDto summary =
        new JobSummaryDto(
            java.util.UUID.fromString("491ff5a4-5745-40bd-8bc3-119582f6e89e"),
            "COMPLETED",
            new JobSummaryDto.Owner("OAUTH_USER", "user-123"),
            java.time.OffsetDateTime.parse("2026-07-14T22:13:24.272673Z"),
            null, null,
            java.time.OffsetDateTime.parse("2026-07-21T22:13:24.272673Z"),
            0, "RESULT_STORE", null);
    when(service.list(any(Principal.class), any(JobQuery.class)))
        .thenReturn(new JobPage(0, 50, 1, 1, List.of(summary)));

    mvc.perform(get("/api/v1/verifications/jobs").with(authentication(authFor(standard))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value("491ff5a4-5745-40bd-8bc3-119582f6e89e"))
        .andExpect(jsonPath("$.content[0].requestedBy.type").value("OAUTH_USER"))
        .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
  }

  @Test
  void bindsRepeatedStatusAndPassesPrincipal() throws Exception {
    when(service.list(any(Principal.class), any(JobQuery.class)))
        .thenReturn(new JobPage(0, 50, 0, 0, List.of()));

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("status", "COMPLETED", "FAILED")
                .param("page", "2")
                .param("size", "10")
                .with(authentication(authFor(privileged))))
        .andExpect(status().isOk());

    ArgumentCaptor<JobQuery> qc = ArgumentCaptor.forClass(JobQuery.class);
    ArgumentCaptor<Principal> pc = ArgumentCaptor.forClass(Principal.class);
    verify(service).list(pc.capture(), qc.capture());
    org.assertj.core.api.Assertions.assertThat(qc.getValue().statuses())
        .containsExactly(JobStatus.COMPLETED, JobStatus.FAILED);
    org.assertj.core.api.Assertions.assertThat(qc.getValue().page()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(pc.getValue().id()).isEqualTo("admin");
  }

  @Test
  void invalidStatusValue_returns400() throws Exception {
    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("status", "NOT_A_STATUS")
                .with(authentication(authFor(standard))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void standardWithOwnerFilter_propagates400FromService() throws Exception {
    when(service.list(any(Principal.class), any(JobQuery.class)))
        .thenThrow(AppException.badRequest("owner filter requires PRIVILEGED"));

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("ownerId", "someone")
                .with(authentication(authFor(standard))))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=VerificationJobListControllerTest`
Expected: FAIL — `VerificationJobListController` does not exist (compilation error).

- [ ] **Step 3: Write the controller**

```java
package org.toresoft.signverify.api;

// <LGPL header block>

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.application.JobPage;
import org.toresoft.signverify.application.JobQuery;
import org.toresoft.signverify.application.JobQueryService;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.security.Principal;

/** Lists verification jobs. Scoping (own vs all) and validation live in {@link JobQueryService};
 * this layer only binds query params and returns the envelope. */
@RestController
@RequestMapping("/api/v1/verifications")
public class VerificationJobListController {

  private final JobQueryService service;

  public VerificationJobListController(JobQueryService service) {
    this.service = service;
  }

  @GetMapping("/jobs")
  public JobPage list(
      @RequestParam(required = false) String ownerId,
      @RequestParam(required = false) PrincipalType ownerType,
      @RequestParam(required = false) List<JobStatus> status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdOn,
      @RequestParam(required = false) OffsetDateTime createdFrom,
      @RequestParam(required = false) OffsetDateTime createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    Principal actor =
        (Principal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    JobQuery query =
        new JobQuery(
            ownerId,
            ownerType,
            status,
            createdOn,
            createdFrom == null ? null : createdFrom.toInstant(),
            createdTo == null ? null : createdTo.toInstant(),
            page,
            size);

    return service.list(actor, query);
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=VerificationJobListControllerTest`
Expected: PASS (4 tests). If the `@WebMvcTest` context fails to load due to missing security collaborators, mirror the `@MockitoBean` set used by `AsyncVerificationControllerTest` (add any beans the error names).

- [ ] **Step 5: Verify ArchUnit still holds**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn test -Dtest=ArchitectureTest`
Expected: PASS. This controller depends only on `application` (`JobQueryService`) and `domain.model` enums — it does NOT touch `..persistence..`, so the `controllers_do_not_use_repositories` rule (which whitelists only the three legacy controllers) is satisfied without adding `VerificationJobListController` to the whitelist.

- [ ] **Step 6: Commit**

```bash
source ~/.sdkman/bin/sdkman-init.sh; mvn spotless:apply
git add src/main/java/org/toresoft/signverify/api/VerificationJobListController.java \
        src/test/java/org/toresoft/signverify/api/VerificationJobListControllerTest.java
git commit -m "feat: add GET /verifications/jobs list endpoint"
```

---

## Task 6: End-to-end integration test + full verify

Prove the scoping and filters end-to-end against real Postgres, then run the whole battery.

**Files:**
- Create: `src/test/java/org/toresoft/signverify/api/VerificationJobListIT.java`

**Interfaces:**
- Consumes: the running application (full `@SpringBootTest`), `ValidationJobRepository`, `MockMvc`.
- Produces: nothing consumed downstream (leaf verification task).

- [ ] **Step 1: Write the IT**

Model it on an existing full-context IT for wiring (find one that persists rows and calls `MockMvc` with an authenticated principal — e.g. how `VerificationControllerIT` or `ExtractionControllerIT` set up `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` and authentication). The IT must assert:

```java
// Pseudocode of the assertions — adapt setup to the codebase's IT harness:
// 1. Persist 3 jobs: two owned by "me" (API_KEY), one by "other" (API_KEY),
//    with distinct createdAt values.
// 2. GET /api/v1/verifications/jobs as STANDARD "me"
//      → 200, content has exactly the 2 "me" jobs, newest first (createdAt DESC).
// 3. GET .../jobs?ownerId=other as PRIVILEGED admin
//      → 200, content has exactly the 1 "other" job.
// 4. GET .../jobs?ownerId=other as STANDARD "me"
//      → 400.
// 5. GET .../jobs?createdOn=<day-of-one-job> as PRIVILEGED
//      → 200, only jobs created that UTC day.
```

Concretely, follow the naming `*IT` so Failsafe runs it under `mvn verify`. Reuse the persistence helper shape from Task 2's `persist(...)` (all NOT NULL fields set). Use the same authentication post-processor as the controller test (`.with(authentication(...))`), or the IT harness's existing auth helper if one exists.

- [ ] **Step 2: Run the IT**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn verify -Dit.test=VerificationJobListIT -Dtest=void`
Expected: PASS. (Testcontainers pulls Postgres on first run.)

- [ ] **Step 3: Full battery**

Run: `source ~/.sdkman/bin/sdkman-init.sh; mvn verify`
Expected: BUILD SUCCESS — all unit + IT green, Spotless check clean, JaCoCo report generated.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/toresoft/signverify/api/VerificationJobListIT.java
git commit -m "test: end-to-end IT for GET /verifications/jobs scoping and filters"
```

---

## Optional follow-up (not required for correctness)

- **DB index for ordered pagination.** If the `validation_job` table is expected to grow large, add a Flyway migration `V8__validation_job_list_indexes.sql` with an index on `(requested_by_principal_id, created_at)` and/or `(created_at)` to support the `ORDER BY created_at DESC` + owner-scoped queries. This is a performance optimization; the feature is correct without it. If added, run `mvn verify` again (Flyway validate + Hibernate schema validate must stay green).

---

## Self-Review

**Spec coverage:**
- Endpoint + query params (owner/status/date/page/size) → Task 4 (contract) + Task 5 (controller).
- Date filter modes (exact day / before / after / between) → Task 3 (`createdOn` expansion, from/to) + Task 2 (range predicates) + Task 6 (IT).
- Owner filter + status IN → Task 2 (`JobSpecifications`) + Task 3.
- Role scoping (STANDARD own-only, PRIVILEGED all, STANDARD+owner → 400) → Task 3 + Task 5 + Task 6.
- Validation (createdOn XOR range, from>to, size clamp, invalid enum → 400) → Task 3 + Task 5.
- Response shape (`JobSummary` fields exactly, no `deliveredAt`, no result/error, DELETED included) → Task 1 + Task 4 (+ IT).
- Safety (no entity dump, no writes/`touchLastAccessed`) → Task 1 (mapping) + Task 3 (read-only service).
- `JpaSpecificationExecutor` on repository → Task 2.
- Typed OpenAPI schemas + regeneration + contract IT → Task 4.
- Optional index migration → Optional follow-up.

**Placeholder scan:** No `TBD`/`TODO`. Task 6 uses guided pseudocode for the IT (setup varies by the codebase's IT harness) with explicit, concrete assertions — the implementer adapts wiring, not behavior.

**Type consistency:** `JobQuery`/`JobPage`/`JobSummaryDto`/`JobQueryService.list(Principal, JobQuery)` signatures and `JobSpecifications` method names are identical across Tasks 1–5. Timestamps are `OffsetDateTime` (UTC) in the DTO, `Instant` in `JobQuery`/specs. `JobSummaryDto`, `JobQuery`, `JobPage`, `JobQueryService` all live in `application`; the `api` controller imports them (direction `api → application`), and `JobSummaryDto`'s package was chosen up front to avoid any `application → api` dependency — no fallback needed. ArchUnit's `controllers_do_not_use_repositories` is satisfied because the new controller uses the service, not a repository.
