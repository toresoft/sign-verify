---
title: "Lessons Learned: Spring Boot 3.5 → 4.1 migration planning (office-hours + eng review)"
type: lessons-learned
source: session
date: 2026-07-11
tags: [lessons-learned, spring-boot-4, jackson-3, migration-planning, dss, tomcat]
lesson_count: 5
category: notes
confidence: high
summary: "Design/review session for the Boot 4.1 migration surfaced a false DSS/Jackson dependency premise, the correct Jackson-3 audit surface, a 4-boundary JSON-migration pin pattern, a hidden Tomcat major bump, and a gap in auth-negative smoke checks."
---

# Lessons Learned: Spring Boot 3.5 → 4.1 migration planning

> Extracted from an `/office-hours` + `/plan-eng-review` session on 2026-07-11 that produced
> and hardened the Boot 4.1 migration design doc. 5 lessons, spanning a wrong premise caught
> by cross-model review, a corrected audit surface, and a reusable migration-pin pattern.

## Lesson 1: DSS 6.4 carries no runtime Jackson dependency

**Category**: correction
**Context**: Drafting Premise 3 of the Boot 4.1 migration plan ("Jackson 3 flip decoupled
from the framework bump"), the first draft assumed DSS "uses Jackson 2 internally" and
therefore constrains the coexistence window.
**Symptom**: The assumption went unchallenged through a 3-round adversarial review of the
design doc (score 9.5/10) — nobody checked it against the actual dependency tree.
**Root cause**: The claim was inherited from general reasoning about "big Java libraries
usually pull in Jackson," never verified. An independent second-opinion subagent (`outside
voice` step of `/plan-eng-review`) read the DSS 6.4 POMs directly in `~/.m2`: the only
Jackson artifact (`jackson-module-jakarta-xmlbind-annotations`) is **test-scope** in
`dss-document`/`dss-validation`/`dss-diagnostic-jaxb`; JAdES uses `jose4j`, not Jackson.
**Fix**: Corrected the premise to "DSS is Jackson-agnostic at runtime"; added a `T4` cleanup
task to drop the `jackson-bom` CVE override and Jackson 2 from the runtime classpath
entirely once the Jackson 3 flip lands, since nothing needs it anymore.
**Rule**: Never let "library X probably needs shared dependency Y" ride as an unverified
premise in a migration plan — run `dependency:tree` (or read the vendor's POMs) before
building coexistence strategy around it. A wrong premise here would have kept an unused,
CVE-prone library on the classpath indefinitely.

## Lesson 2: The Jackson-3 migration audit surface is `ObjectMapper` usage, not `catch(IOException)`

**Category**: correction
**Context**: Planning T3a (code prep for the Jackson 2→3 flip), the first draft told
implementers to audit "~12 `catch (IOException)` sites nourishing the future
`JsonProcessingException`→`JacksonException` hierarchy change" (Jackson 3's
`JacksonException` extends `RuntimeException`, not `IOException`).
**Symptom**: A `grep` for `catch (IOException` in `src/main/java` returns ~12 hits, but most
are filesystem I/O in `ExtractionWorkspace`, `FilesystemDocumentStorageAdapter`, and
`ExtractionTempCleanupScheduler` — completely unrelated to Jackson.
**Root cause**: Conflating "same exception-catch syntax" with "same migration risk." The
actual risk surface is wherever an `ObjectMapper` is injected and used for
serialize/deserialize — a different, much smaller set.
**Fix**: Re-scoped the audit to the 7 files that actually inject `ObjectMapper`
(`VerificationController`, `DssValidatorAdapter`, `ValidationWorker`,
`AsyncVerificationController`, `TsdAwareValidatorAdapter`, `CallbackWorker`,
`AuditService`), flagging `AuditService.java:197`
(`catch (JsonProcessingException)`) as the one guaranteed compile break.
**Rule**: When migrating a serialization library, find the audit surface by grepping the
mapper/library **type** itself (e.g. `ObjectMapper`), not by grepping a generic
exception-catch pattern that overlaps with unrelated concerns (here, plain file I/O).

## Lesson 3: Serialization-library migrations need pins at 4 JSON boundaries, not 2

**Category**: pattern
**Context**: Test-review pass on the migration plan initially proposed golden pins only for
(a) malformed-request error mapping and (b) HTTP response wire format (date/timestamp
shape) — the two boundaries visible from the controller layer.
**Symptom/gap**: An independent review pass (`outside voice`) found two more JSON
boundaries with zero test coverage: (c) HMAC-signed outbound callback payloads
(`CallbackWorker`) — where a format drift means the receiver's signature check fails and
the callback is silently rejected — and (d) JSON persisted in Postgres
(`profileOverrides`/metadata/audit details, written by `AsyncVerificationController` and
`ValidationWorker`) and reread by **in-flight jobs across the deploy boundary** — a job
queued before the flip and picked up after it must still deserialize.
**Fix**: Added 2 more golden pins to the plan: a callback-payload wire-format pin and a
JSON-reread pin (fixture captured pre-flip, deserialized by the post-flip mapper in every
subsequent stage).
**Rule**: For any Jackson-major-version (or similar serialization library) migration, audit
JSON boundaries in this order — HTTP request in, HTTP response out, anything persisted and
reread later, anything signed/hashed and sent externally. The last two are the ones a
controller-focused review misses, and they fail silently (a rejected callback, a stuck
job) rather than loudly (a 500).

## Lesson 4: A Spring Boot major bump can hide an embedded-server major bump

**Category**: gotcha
**Context**: Building the T0 spike inventory for the Boot 3.5→4.1 migration.
**Symptom**: The pom already carries a `tomcat.version` override (10.1.55, for a 2026 CVE),
so the first-draft plan treated Tomcat purely as "an override to remove once Boot 4.1
manages the version" — the fact that Boot 4 requires Servlet 6.1, i.e. **Tomcat 11 (a major
version bump)**, was never named.
**Root cause**: An existing CVE-override property in the pom visually signals "this is
already being tracked," which suppresses the instinct to check whether the *version family*
is also about to change underneath it.
**Fix**: Added Tomcat 11 explicitly to the T0 inventory (connector config, multipart/header
limits, URI parsing defaults — all stricter in Tomcat 11) and a multipart-upload-at-limits
test to the T2 smoke gate, since this service's primary path is signed-file upload.
**Rule**: When a framework major bump is planned, explicitly check every transitively-bumped
*major* version of an embedded runtime component (servlet container, DB driver major,
JVM baseline) — don't let an existing CVE-override line for that component read as "already
handled."

## Lesson 5: Auth-negative checks belong in every migration smoke gate that touches security defaults

**Category**: pattern
**Context**: Defining the T2 (Boot 4.1 bump) smoke-test checklist.
**Symptom/gap**: The first-draft checklist verified only positive paths — clean startup, one
successful e2e verification, `actuator/health` UP. It didn't check that previously-fixed
security regressions on this exact service (`/actuator/prometheus` requiring auth — commit
f0fa685; API-key `SecurityContext` surviving async dispatch — commit 68baacf; JWT audience
validation — commit 8253a9b) still held after the Security 7 + actuator-Boot-4 jump.
**Root cause**: Smoke checklists default to "does it work," which is the wrong frame for a
migration whose biggest risk category is "did the framework's new defaults quietly loosen
something that used to be enforced."
**Fix**: Added 3 explicit auth-negative assertions to the T2 gate: unauthenticated
`/actuator/prometheus` → 401/403, unauthenticated API-key endpoint → 401, wrong-audience
JWT → 401.
**Rule**: For any migration touching a security/auth framework layer, the smoke gate needs
at least one negative assertion per prior security fix in that area — "still forbidden"
checks are the only sentinel for silently-loosened defaults, and positive-path smoke tests
will never catch them.

## Related process note

This session ran a 3-round adversarial review of the design doc (office-hours, 9.5/10) and
a separate independent second-opinion pass (`/plan-eng-review`'s outside voice) — the
second pass, with fresh context and no memory of the first review's reasoning, is what
caught Lessons 1, 3, 4, and 5. The first review caught Lesson 2. Neither pass alone found
everything; running both in sequence is what surfaced the full set.
