---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-008
volatility: warm
---

# sign-verify-2

Spring Boot 3.4 / Java 21 service (artifact `org.toresoft:sign-verify-2:1.0.0-SNAPSHOT`) that **verifies digital signatures** under the EU eIDAS framework (PAdES/CAdES/XAdES/JAdES/ASiC) using the [[entities/dss]] library and EU [[concepts/trusted-lists]]. It is the primary subject of this wiki.

## What it does
- **Verify** signatures synchronously or asynchronously ([[concepts/async-verification-jobs]]) with selectable [[concepts/validation-profiles]].
- **Extract** the original unsigned document(s) from a signed file.
- **Manage** validation profiles, API keys, TSL refresh, and an audit log.
- Deliver async results via HMAC-signed webhooks.

## Architecture
[[concepts/hexagonal-architecture]] (hexagonal-lite): high-risk integrations (DSS, OAuth, callback HTTP, filesystem, hashing) sit behind **domain ports** with **adapters** as implementations, enforced by ArchUnit. DB schema owned by Flyway; design-first [[concepts/design-first-openapi]] at `src/main/resources/openapi/openapi.yaml`.

Domain ports: `SignatureValidatorPort`, `ExtractionPort`, `TslRefresherPort`, `CallbackDispatcherPort`, `DocumentStoragePort`, `PasswordHasherPort`, `SecretCipherPort`.

## Key subsystems
- **Auth:** API key (`sv_<prefix>_<body>`, bcrypt-hashed, prefix-indexed) + optional OAuth2 JWT. Roles PRIVILEGED / standard.
- **TSL:** [[concepts/tsl-hot-swap-refresh]] via `TrustedListsCertificateSource`, ShedLock-coordinated, OJ keystore as LOTL anchor.
- **Load protection:** sync semaphore (429), async back-pressure, DSS [[concepts/circuit-breaker]], [[concepts/shedlock]] scheduler.
- **Errors:** `application/problem+json` (RFC 9457) with stable `urn:signverify:error:<code>` codes.

## Source of truth
- Design & architecture: [[sources/SRC-2026-06-27-002]]
- API contract: [[sources/SRC-2026-06-27-008]]
- Operational docs: [[sources/SRC-2026-06-27-003]] … 007

## Known gaps
`AuditService` is **partially wired**: `TslRefreshScheduler` (6 paths), `AsyncVerificationController` (access-denial path), and `TslController` (manual refresh) call `audit.log`. Still missing from the sync `VerificationController` and `ValidationWorker` paths. See [[sources/SRC-2026-06-27-007]] §6.3 and the `wire-audit-log` plan.

## Related
- [[entities/dss]] · [[entities/eidas-regulation]] · [[concepts/validation-profiles]]
- [[concepts/async-verification-jobs]] · [[concepts/trusted-lists]] · [[concepts/problemjson]]
- [[sources/openapi-spec-sign-verify-2]] — endpoint/schema reference completa del contratto API
