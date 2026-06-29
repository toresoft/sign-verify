---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-004
volatility: warm
---

# TSL hot-swap refresh

How [[entities/sign-verify-2]] refreshes [[concepts/trusted-lists]] **without interrupting in-flight verifications**: build a new `TrustedListsCertificateSource` from a fresh `TLValidationJob` run, then **atomically swap** the live source. In-flight validations keep using the previously-resolved trust material; new validations pick up the refreshed mirror.

## Configuration
```yaml
app.tsl:
  sources:                # list of LOTL/TL; empty → hardcoded eu-lotl
    - { id: eu-lotl, type: LOTL, url: https://ec.europa.eu/tools/lotl/eu-lotl.xml,
        pivot-support: true, oj-keystore-path: classpath:keystore/oj-keystore.p12 }
  refresh:
    cron: "0 0 2 * * *"
    startup-mode: BACKGROUND   # BACKGROUND | BLOCKING | SKIP
```

## Coordination & history
- Refreshes run scheduled (cron), on startup, or manual (force). **ShedLock** ensures a single execution across instances.
- Each run writes a `tsl_refresh` row: `SUCCESS`/`PARTIAL`/`FAILED` + counts (`certificates_added`/`removed`/`unchanged`).
- `tsl_refresh.trigger`: `SCHEDULED` | `MANUAL` | `STARTUP`.

## API surface
- `GET /api/v1/tsl/status` — last refresh, per-source outcome, cert counts.
- Force-refresh and [[concepts/oj-keystore]] management are **PRIVILEGED**.

## Related
- [[concepts/trusted-lists]] · [[entities/dss]] · [[concepts/shedlock]]
- [[concepts/oj-keystore]] · [[entities/sign-verify-2]]
