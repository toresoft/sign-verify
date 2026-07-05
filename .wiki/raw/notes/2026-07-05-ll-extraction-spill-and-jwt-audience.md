---
title: "Lessons Learned: extraction filesystem-spill (M2) and JWT audience validation (H2)"
type: lessons-learned
source: session
date: 2026-07-05
tags: [lessons-learned, extraction, dos, streaming, temp-files, oauth, jwt, security, docker, planning]
lesson_count: 6
category: notes
confidence: high
summary: "Spilling extraction to disk only fixes M2 if the temp dir is real disk (not the container's tmpfs) and caps stay; StreamingResponseBody dictates temp-file lifecycle; the default JWT resource-server decoder never checks aud."
---

# Lessons Learned: extraction filesystem-spill (M2) and JWT audience validation (H2)

> Extracted from session on 2026-07-05. 6 lessons covering the M2 recursive-extraction DoS fix (spill-to-disk + caps + orphan sweeper), the H2 JWT cross-audience token-confusion fix, and one plan-authoring pattern that surfaced during subagent-driven execution.

## Lesson 1: Spilling to `/tmp` in the hardened container spills to RAM, not disk

**Category**: gotcha
**Context**: Fixing M2 (heap exhaustion in recursive container extraction) by writing extracted leaves to temp files instead of holding `byte[]` in heap.
**Symptom**: The fix looked complete but would have been cosmetic in production.
**Root cause**: `docker-compose.prod.yml` runs `read_only: true` with `/tmp` mounted as `tmpfs:/tmp:size=128m` (RAM-backed) and a 1g memory limit. The app default temp dir is `${java.io.tmpdir}` = `/tmp`. Spilling there consumes the same cgroup memory budget (OOM-kill anyway) and caps out at 128MB. The only writable disk path is the `svdata` volume at `/var/lib/sign-verify`.
**Fix**: `Dockerfile` creates `/var/lib/sign-verify/extract-tmp`; prod compose sets `APP_EXTRACTION_TEMP_DIR=/var/lib/sign-verify/extract-tmp`. App default stays `${java.io.tmpdir}` for dev portability.
**Rule**: Before "spill to filesystem" counts as a memory fix, confirm the target dir is disk-backed on the actual deployment — in a hardened container `/tmp` is usually a small RAM tmpfs and the root fs is read-only, so only a mounted volume qualifies.

## Lesson 2: Spill-to-disk without caps just moves the DoS from heap to disk

**Category**: rule
**Context**: Same M2 fix; deciding whether the spill alone closed the finding.
**Root cause**: A decompression/nesting bomb that would OOM the heap will instead fill the disk once you spill. Depth was already bounded (`MAX_DEPTH`), but leaf count and cumulative bytes were not.
**Fix**: `ExtractionWorkspace.spill` enforces per-leaf, cumulative, and leaf-count ceilings fail-fast during the streaming copy (checked before each `write`), throwing `AppException.tooLarge` (413) before the offending chunk lands; the partial file is deleted.
**Rule**: Moving a resource-exhaustion surface (heap→disk) is not a fix by itself — add explicit ceilings on the new resource and fail-fast, or you have just changed which resource the attacker exhausts.

## Lesson 3: `StreamingResponseBody` runs after the controller returns — so temp-file cleanup lives in the stream's `finally`

**Category**: gotcha
**Context**: Streaming extracted leaves from temp files back to the client and cleaning them up afterwards.
**Symptom**: A controller-scope `try (result) { return body; }` would delete the temp files before the body streams (empty/failed download).
**Root cause**: Spring invokes the `StreamingResponseBody` lambda after the controller method has already returned. Any try-with-resources in the controller closes too early.
**Fix**: `ExtractionResult` is `AutoCloseable` (owns the workspace); it is closed inside the streaming body's `finally` (both single-file and ZIP paths), and closed immediately in a `catch` only for errors thrown before streaming starts.
**Rule**: When a resource must outlive the controller method because an async/streaming callback consumes it, make the result `AutoCloseable` and close it inside the callback's `finally` — never in a controller-scope try-with-resources.

## Lesson 4: A node-local temp sweeper must NOT use `@SchedulerLock`

**Category**: correction
**Context**: Adding a scheduled cleaner for orphaned extraction temp dirs, mirroring the existing `JobCleanupScheduler`.
**Root cause**: `JobCleanupScheduler` is `@SchedulerLock`-ed because it cleans shared state (DB rows / object storage) — exactly one instance should run it. Extraction temp files live on each instance's local filesystem; a global lock would let one instance run while every other instance's orphans are never collected.
**Fix**: `ExtractionTempCleanupScheduler` is `@Scheduled` with NO ShedLock, so every instance sweeps its own disk.
**Rule**: ShedLock is for schedulers that mutate shared state; a scheduler that acts on node-local resources (local temp dirs, per-pod caches) must run unlocked on every instance.

## Lesson 5: The issuer-based JWT resource-server decoder validates everything except `aud`

**Category**: discovery
**Context**: Evaluating H2 — a JWT signed by the realm IdP for another client being accepted here (cross-audience token confusion → `PRIVILEGED` via the `roles` claim).
**Root cause**: Spring's default decoder (`JwtValidators.createDefaultWithIssuer`) checks signature + issuer + exp/nbf but not audience. `SecurityConfiguration` wired only `jwtAuthenticationConverter`, no `OAuth2TokenValidator` for `aud`. Verified in the Boot 3.5.14 autoconfigure jar that `OAuth2ResourceServerJwtConfiguration$JwtDecoderConfiguration` only adds an audience `JwtClaimValidator` when the `spring.security.oauth2.resourceserver.jwt.audiences` property is set — which it was not.
**Fix**: Own `AudienceValidator implements OAuth2TokenValidator<Jwt>` (rejects empty config to avoid failing open) composed with the issuer default on a custom `JwtDecoder` bean, gated `@ConditionalOnProperty(app.security.oauth.enabled=true)`; accepted audience via `app.security.oauth.audience` (default `sign-verify`). Chose a bean + unit-tested validator over the property-only fix because the test profile has no live IdP, so a property-only change has no automated regression guard.
**Rule**: An issuer-only JWT resource server accepts any same-realm token — always add an audience validator; prefer an own testable `OAuth2TokenValidator` over the `audiences` property when the test suite can't exercise a real decoder, and make it fail-closed on empty config. Enabling `aud` validation is a breaking change that needs the IdP to emit that audience (Keycloak audience mapper) or all tokens 401.

## Lesson 6: An atomic contract change cannot be split into independently-green plan tasks

**Category**: pattern
**Context**: Executing the M2 plan via subagent-driven development; the plan split the `ExtractedFile` record change (`byte[] content` → `Path path, long size`) into separate Task 2 (workspace) / Task 3 (adapters) / Task 4 (controller+tests).
**Symptom**: Task 2 could not reach a green `mvn test` — changing the record broke the adapters, controller, and their tests, so neither main nor test sources compiled until all of them moved together. The implementer correctly stopped at `DONE_WITH_CONCERNS`.
**Root cause**: A record/interface signature change is compile-coupled across every producer and consumer plus their tests; there is no intermediate state that compiles.
**Fix**: Merged Tasks 2–4 into one atomic migration commit and reviewed them together.
**Rule**: When writing a plan, keep a data-contract/signature change and all its producers, consumers, and tests in a single task — task boundaries must fall where an intermediate build is green, and a contract change has no such seam.
