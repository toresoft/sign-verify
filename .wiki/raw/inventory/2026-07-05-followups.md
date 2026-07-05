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
