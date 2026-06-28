---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-004
---

# Trusted Lists (TL / LOTL)

The EU mechanism for establishing **trust anchors** for signature validation under [[entities/eidas-regulation]]. Each member state publishes a Trusted List (TL); the EU publishes the **List of Trusted Lists (LOTL)** that points to all national TLs (with **pivot** indirection supported).

## Structure
- **LOTL** (`https://ec.europa.eu/tools/lotl/eu-lotl.xml`) → signed, points to national TLs.
- **TL** per country → lists trusted service providers, their certificates, service statuses, and qualifiers.
- The **OJ keystore** (`oj-keystore.p12`) holds the Official Journal certificates used to verify the LOTL signature — the ultimate trust anchor.

## In DSS / sign-verify-2
- `TLValidationJob` + `LOTLSource`/`TLSource` + `TrustedListsCertificateSource` form the loading pipeline ([[entities/dss]] §11).
- sign-verify-2 performs [[concepts/tsl-hot-swap-refresh]]: refresh into a new source, then atomically swap the live `TrustedListsCertificateSource`; history rows in `tsl_refresh` (`SUCCESS`/`PARTIAL`/`FAILED`, cert add/remove/unchanged counts).
- Refresh is scheduled (cron), ShedLock-coordinated across instances, with `startup-mode: BACKGROUND|BLOCKING|SKIP`.
- A queryable `trusted_certificate` mirror is populated after refresh.

## Common failure
National TSLs failing with `PKIX path building failed` usually mean a **stale/missing OJ keystore** or a pivoted LOTL URL — rebuild the keystore when a new Official Journal is published ([[sources/SRC-2026-06-27-004]] §3.7).

## Related
- [[entities/dss]] · [[concepts/tsl-hot-swap-refresh]] · [[concepts/oj-keystore]]
- [[entities/eidas-regulation]] · [[entities/sign-verify-2]] · [[concepts/shedlock]]
