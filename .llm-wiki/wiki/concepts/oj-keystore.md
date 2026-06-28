---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-004
---

# OJ keystore (LOTL trust anchor)

The **Official Journal keystore** (`oj-keystore.p12`, bundled in the image) holding the EU Official Journal certificates that [[entities/dss]] uses to verify the signature on the [[concepts/trusted-lists|LOTL]]. It is the ultimate trust anchor for the whole EU trust chain.

## Management
- Bundled as `classpath:keystore/oj-keystore.p12`; password from env (`oj-keystore-password-env`).
- Must be **rebuilt manually when a new Official Journal is published** — rebuilding is a PRIVILEGED operation ([[sources/SRC-2026-06-27-004]] §3.7).
- Staleness is the usual cause of national TSLs failing with `PKIX path building failed`.

## Related
- [[concepts/trusted-lists]] · [[concepts/tsl-hot-swap-refresh]] · [[entities/dss]]
- [[entities/sign-verify-2]]
