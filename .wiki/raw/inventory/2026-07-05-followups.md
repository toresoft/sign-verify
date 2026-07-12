---
title: "Inventory: extraction/security follow-ups"
type: inventory
source: session
date: 2026-07-05
tags: [inventory, follow-up, extraction, security, oauth, flaky-tests, deploy]
category: inventory
confidence: high
summary: "Three tracked follow-ups from the M2 extraction spill and H2 JWT audience work: a deferred heap-guard hardening, an IdP deploy prerequisite, and a full-suite flaky-test watch."
---

# Inventory: extraction/security follow-ups

> Tracked items with status / priority / next action. Raw lessons in [[2026-07-05-ll-extraction-spill-and-jwt-audience]].

## INV-1: `readNode` transient heap guard (M2 residual)

- **Type**: hardening / tech-debt
- **Status**: open (deferred at final review)
- **Priority**: low
- **Origin**: opus whole-branch review of `feat/extraction-fs-spill` (merged `de882d8`); logged in `.superpowers/sdd/progress.md` backlog.
- **Detail**: `RecursiveExtractionAdapter.readNode` reads each recursion node fully into a `byte[]` before the per-leaf cap is checked at spill time. A single inflated node (e.g. an ASiC decompression bomb) can transiently exceed `maxLeafBytes` in heap before the 413 fires. Bounded by `input × MAX_DEPTH`, NOT the old whole-tree `O(B^depth)`; disclosed in the spec as a known limit — not the M2 defect.
- **Next action**: add a size guard in `readNode` (cap the read against `maxLeafBytes` while streaming from `DSSDocument.openStream()`), or document as accepted.
- **Refs**: `adapter/dss/RecursiveExtractionAdapter.java` (readNode); spec `docs/superpowers/specs/2026-07-05-extraction-filesystem-spill-design.md` §4.

## INV-2: H2 deploy prerequisite — IdP audience mapper

- **Type**: deploy prerequisite / blocker
- **Status**: open — MUST precede enabling H2 in any OAuth environment
- **Priority**: high (gates the H2 rollout)
- **Origin**: H2 fix, merged `56ae1a7` (`fix(security): validate JWT audience …`).
- **Detail**: the new `AudienceValidator` rejects tokens whose `aud` does not contain `app.security.oauth.audience` (default `sign-verify`). Keycloak sets `aud` to the client the token was minted for, so this service's tokens carry `sign-verify` only if an **audience mapper** is configured on the IdP. Without it, every token is rejected with 401.
- **Next action**: configure the Keycloak audience mapper (or set `APP_SECURITY_OAUTH_AUDIENCE` to the value the IdP actually emits) BEFORE deploying with `app.security.oauth.enabled=true`; add a smoke check post-deploy.
- **Refs**: `config/JwtDecoderConfiguration.java`, `security/AudienceValidator.java`, `application.yaml` (`app.security.oauth.audience`).

## INV-3: full-suite flaky-test watch

- **Type**: test reliability / watch item
- **Status**: monitoring (not a regression — pre-existing)
- **Priority**: medium
- **Origin**: observed during `mvn verify` across the M2 work; verified NOT caused by the diff (tests pass standalone).
- **Detail**: `TsdSmokeTest` and `ProfileSeederTest` (both `@SpringBootTest(webEnvironment=RANDOM_PORT)`) intermittently fail only under full-suite resource contention — `TsdSmokeTest` throws `ResourceAccessException: localhost:<port> failed to respond` (embedded server not up under load). Both pass 100% standalone.
- **Next action**: if it recurs in CI, isolate the heavy `@SpringBootTest` classes (Failsafe/forkCount tuning, or a dedicated group), or add a startup/readiness wait; until then, re-run standalone to confirm green.
- **Refs**: `adapter/dss/TsdSmokeTest.java`, `application/ProfileSeederTest.java`.

## INV-4: springdoc Jackson 3 migration — gates full Jackson 2 removal

- **Type**: upstream dependency watch / blocker
- **Status**: open — BLOCKS Phase 3 of the Jackson 2 removal plan
- **Priority**: medium (Jackson 2 residual is patched, no known CVE, so not urgent)
- **Origin**: Boot 4.1 migration (merged `9f4d831`); full-tree analysis found 3 Jackson 2 bringers, springdoc is the only one without a Jackson-3-native release.
- **Detail**: `springdoc-openapi-starter-webmvc-ui` (2.7.0 now; even the Boot-4-compatible 3.0.x) still depends on Jackson 2 via `swagger-core-jakarta:2.2.25` (`jackson-dataformat-yaml` + `jackson-datatype-jsr310`). Jackson 3 migration tracked upstream in springdoc issues #3200, #3216 (closed as duplicate), #3169 — no Jackson-3-only release as of 2026-07-11. Until then the `jackson-bom:2.22.1` override in `pom.xml` must stay to keep the residual Jackson 2 patched.
- **Next action**: watch springdoc releases for a Jackson-3-only build; when it lands, execute Phase 3 of [[../../../docs/superpowers/plans/2026-07-11-jackson2-removal]] (bump springdoc, remove jackson-bom override, verify empty `com.fasterxml.jackson` tree). Fallback if it stalls: drop springdoc runtime and serve static `openapi.yaml` + swagger-ui webjar.
- **Refs**: `pom.xml` (jackson-bom override ~L59-73, springdoc dep); `docs/superpowers/plans/2026-07-11-jackson2-removal.md` Phase 3; lessons [[2026-07-11-ll-boot4-migration-execution]].
