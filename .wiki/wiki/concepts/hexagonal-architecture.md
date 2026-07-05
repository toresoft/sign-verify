---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-07-03
sources:
  - sources/SRC-2026-06-27-002
volatility: warm
---

# Hexagonal architecture (ports & adapters)

The architectural pattern used by [[entities/sign-verify-2]]: "hexagonal-**lite**" — a single Maven module with classic Spring layering, but high-risk/complex external integrations are isolated behind **domain ports** (interfaces) with **adapters** as the implementations. Enforced at build time by **ArchUnit**.

## Rationale (from the design spec)
Balances speed of development (familiar Spring structure) with **testability of critical adapters** — [[entities/dss]] is heavy and hard to invoke in unit tests when real TSL/cert data is involved, so isolating it behind a port lets the core be tested without it.

## Package map
```
api          → controllers + DTO mapping
application  → use-case services
domain       → model / port (interfaces) / exception
adapter      → dss / storage / callback / crypto (port impls)
persistence  → Spring Data JPA repositories
security     → API-key filter, principal, role resolver
config       → beans, DSS beans, async pool, scheduler
```

## Ports & adapters (sign-verify-2)
| Port | Adapter |
|------|---------|
| `SignatureValidatorPort` | `DssValidatorAdapter` |
| `ExtractionPort` | `DssExtractionAdapter` |
| `TslRefresherPort` | `DssTslAdapter` (TLValidationJob) |
| `CallbackDispatcherPort` | `HmacCallbackDispatcherAdapter` |
| `DocumentStoragePort` | `FilesystemDocumentStorageAdapter` |
| `PasswordHasherPort` | `BcryptPasswordHasherAdapter` |
| `SecretCipherPort` | `AesGcmSecretCipherAdapter` |
| `TrustedListMirrorPort` | `TrustedCertificateMirror` (DB mirror of TSL certs) |

## Lessons (2026-07-03)
- **Port design with vendor types**: `TrustedListMirrorPort.sync()` takes **no arguments** — the DSS `TrustedListsCertificateSource` would be illegal in `domain/port`, so the adapter receives it by constructor injection instead. When an adapter method's argument is a vendor type, move the argument into the adapter, don't type it in the domain.
- **ArchUnit gap fixed**: the original rules never forbade application→adapter; `TslService` imported `adapter.dss.TrustedCertificateMirror` undetected. Rule `application_does_not_depend_on_adapters` added (failed with 6 violations before the port refactor). Every architectural arrow you rely on needs its own explicit rule. (From [[raw/notes/2026-07-03-ll-code-review-fixes-hardening]].)

## Related
- [[entities/sign-verify-2]] · [[entities/dss]] · [[concepts/async-verification-jobs]]
- [[concepts/circuit-breaker]] (DSS adapter resilience)
