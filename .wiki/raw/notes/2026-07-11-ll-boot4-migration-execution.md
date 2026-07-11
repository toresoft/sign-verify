---
title: "Lessons Learned: Spring Boot 4.1 migration execution — review, fix-verification, merge"
type: lessons-learned
source: session
date: 2026-07-11
tags: [lessons-learned, spring-boot-4, jackson-3, maven, spring-security-test, code-review]
lesson_count: 4
category: notes
confidence: high
summary: "Executing the Boot 4.1 migration (branch feat/t1-golden-pins): multi-agent diff review caught a real CVE regression via dependency:tree, but two of its cleanup suggestions were disproven only by actually running the tests after applying them — and 'patch the vulnerable version' turned out not to satisfy 'remove the old dependency'."
---

# Lessons Learned: Spring Boot 4.1 migration execution

> Extracted from the session that reviewed, fixed, committed and merged the Boot 3.5.14→4.1.0
> migration branch (`feat/t1-golden-pins`, commit `c5c1627`, merge `9f4d831`). 4 lessons, all
> from the review/apply/verify loop rather than the migration mechanics themselves.

## Lesson 1: An imported BOM never overrides a version the parent POM manages directly

**Category**: gotcha
**Context**: A code-review finding proposed collapsing 7 individually-pinned JUnit
`dependencyManagement` entries (`junit-jupiter`, `-api`, `-engine`, `-params`,
`junit-platform-commons/-engine/-launcher`, all pinned to `${junit-jupiter.version}` = 6.0.3)
into a single `org.junit:junit-bom` import — the same idiom already used for `sentry-bom`
two entries above it in the same file.
**Symptom**: Applying the "simplification" and rerunning `mvn test` produced 21 test errors
(`ClasspathAlignmentCheckingLauncherInterceptor` chain failures) across nearly every test
class. `mvn dependency:tree` showed `junit-jupiter-api:5.14.3` / `junit-platform-engine:1.14.3`
— the exact old versions the override existed to defeat — meaning the BOM import had silently
lost.
**Root cause**: Maven's dependency-management precedence: a version declared directly in a
POM's own `dependencyManagement` (here, in `spring-boot-starter-parent` — which pins JUnit
directly, not via an imported BOM) wins over a version arriving through an `<scope>import</scope>`
BOM, regardless of declaration order. The original 7 explicit entries worked specifically
*because* they are direct declarations, not because they were verbose.
**Fix**: Reverted to the 7 explicit entries, adding a comment stating the precedence rule so
the next person doesn't retry the "simpler" BOM-import form.
**Rule**: When a version override exists to fight something the parent POM pins *directly*,
only a direct `dependencyManagement` entry can win — a BOM import of the same artifact family
will silently lose to the parent, even though BOM-import is the more idiomatic Maven pattern
in every other case. Verify with `dependency:tree` after any override refactor, not just after
the original override.

## Lesson 2: A line that "looks redundant" next to a test helper can be the one thing actually read by the code under test

**Category**: gotcha
**Context**: A review finding flagged `SecurityContextHolder.getContext().setAuthentication(...)`
in two `@WebMvcTest` methods (`AsyncVerificationControllerTest.getJob_returns404_forNonOwner`,
`getJob_allowsPrivilegedUser_toViewAnyJob`) as dead code, since the very next line already
calls `mockMvc.perform(...).with(authentication(...))` — the Spring Security Test
request-post-processor that every *other* test in the class relies on alone.
**Symptom**: Removing the two `SecurityContextHolder` lines and rerunning the suite failed
`getJob_returns404_forNonOwner` with `Status expected:<404> but was:<200>` — the "non-owner"
principal never took effect, so the ownership check passed as if the default (`owner`, set in
`@BeforeEach`) principal was still active.
**Root cause**: The controller under test reads the principal via
`SecurityContextHolder.getContext().getAuthentication()` directly (not through a
Spring-Security-managed request attribute the `.with(authentication(...))` post-processor
populates for the *filter chain*), so in this `@WebMvcTest` slice the two mechanisms are not
interchangeable — the holder-set is what the controller actually observes.
**Fix**: Reverted both lines.
**Rule**: "This looks like it duplicates the line right below it" is not evidence of dead code
in tests that straddle two different auth-plumbing mechanisms (thread-local `SecurityContextHolder`
vs. MockMvc request post-processors) — run the specific test after removing the "duplicate"
before trusting the read.

## Lesson 3: "Patch the vulnerable version" and "remove the old major line" are different asks — always confirm which one is meant

**Category**: correction
**Context**: After fixing the jackson-bom CVE regression (Lesson 4 below) by re-importing
`jackson-bom:2.22.1`, proposed "path A: keep Jackson 2 patched" vs. "path B: purge it
entirely." The user said "non voglio dipendenze con il vecchio jackson" (don't want
dependencies on the old Jackson) — a request for B, not A.
**Symptom/gap**: A first look at the dependency tree suggested the only Jackson-2 bringer was
`jackson-databind-nullable:0.2.6` (already a documented follow-up in CLAUDE.md). Re-checking
`dependency:tree` fully surfaced two more compile-scope bringers: `logstash-logback-encoder:7.4`
(JSON logging, no Jackson-3 variant) and `springdoc-openapi-starter-webmvc-ui:2.7.0` →
`swagger-core-jakarta:2.2.25` (Boot-3-line, Jackson-3/Boot-4 readiness unconfirmed).
**Root cause**: The originally-planned fix (jackson-bom re-import) only satisfies "no known
CVE" — it does not satisfy "no Jackson 2 at all," which requires replacing every dependency
that still ships Jackson 2 internally, some of which may not have a drop-in Jackson-3
replacement yet.
**Fix**: Scoped the session's change to path A (patch, CVE-closed, verified via `dependency:tree`)
and logged the full 3-bringer purge as an explicit separate follow-up rather than
half-attempting it under time pressure.
**Rule**: When a user says "I don't want the old dependency," check the *full* transitive
dependency tree for every bringer before proposing a fix — a single BOM/version override
that closes the CVE is not the same deliverable as removing the library family, and the gap
between them (unready downstream libraries) can turn a two-line fix into a multi-dependency
migration that shouldn't be rushed into the same branch.

## Lesson 4: Independent multi-agent review with tool-verified claims caught a real CVE regression that diff-reading alone would have missed

**Category**: pattern
**Context**: Running an 8-angle (compressed to 3-agent) code review over the Boot 4.1
migration diff. One review angle flagged that `pom.xml` deleted the `jackson-bom` CVE
override alongside the (legitimately redundant) tomcat/postgresql/spring-security/logback
overrides, on the assumption the 4.1.0 parent BOM now covers all of them.
**Symptom**: The claim could easily have been treated as equally "probably fine" as the other
four dropped overrides (which the same agent *did* verify safe). Instead the agent ran
`mvn dependency:tree` itself and found `com.fasterxml.jackson.core:jackson-databind:2.21.4:compile`
— reverted below the patched 2.22.1, since Boot 4.1's parent manages only Jackson 3
(`tools.jackson`), not the legacy Jackson 2 family still pulled in transitively.
**Root cause**: "The new parent BOM probably manages this too" is a plausible-sounding
generalization from four confirmed-safe removals to a fifth, structurally different one
(Jackson 2 vs. Jackson 3 are different artifact families under Boot 4.1, so "parent manages
Jackson now" does not extend to the old family) — a diff-only read would not surface this
distinction without resolving the actual dependency graph.
**Fix**: Re-imported `jackson-bom:2.22.1` explicitly with a comment naming the specific
transitive bringers (springdoc, logstash, jackson-databind-nullable) so the reasoning survives
the next dependency bump.
**Rule**: In dependency-management diffs, "close enough to the other N confirmed-safe
removals" is not sufficient evidence for the (N+1)th — verify every distinct artifact family
individually with `dependency:tree`, especially across a major-version bump where the new
parent's managed scope (e.g., Jackson 3 only) may not overlap with what the old override was
protecting (Jackson 2).
