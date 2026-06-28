---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-008
---

# Design-first OpenAPI

The API-contract methodology of [[entities/sign-verify-2]]: the OpenAPI 3 spec (`src/main/resources/openapi/openapi.yaml`) is the **authoritative source**; the OpenAPI Generator produces controller interfaces (`api.spi`) and DTOs (`api.dto`) from it, and an integration test (`OpenApiContractIT`) guards that the code stays in sync.

## Flow
```
openapi.yaml  ──generator──►  api.spi (interfaces) + api.dto (DTOs)
      │                              │
      └── OpenApiContractIT ◄────────┘  (guards conformance)
```

Controllers are thin: they implement generated interfaces, never leak entities, and delegate to application services ([[concepts/hexagonal-architecture]]).

## Implications
- To change an endpoint: edit the YAML first, then regenerate; the contract test fails if drift appears.
- Errors follow [[concepts/problemjson]] with stable URN codes declared in the spec.
- Resource tags: ApiKeys, Profiles, Verifications, Extractions, Tsl, Audit, Health.

## Related
- [[entities/sign-verify-2]] · [[concepts/problemjson]]
- [[concepts/validation-profiles]] · [[concepts/async-verification-jobs]]
