---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-004
volatility: warm
---

# DssTslAdapter

The [[entities/sign-verify-2]] adapter implementing the `TslRefresherPort` ([[concepts/hexagonal-architecture]]). It wraps [[entities/dss]]'s `TLValidationJob` to drive the [[concepts/tsl-hot-swap-refresh]]: downloads [[concepts/trusted-lists|LOTL/TL]] sources, verifies them against the [[concepts/oj-keystore|OJ keystore]], and atomically swaps a freshly-built [[concepts/trusted-lists-certificate-source|TrustedListsCertificateSource]] into the live trust store.

Each run writes a [[entities/tsl_refresh|tsl_refresh]] history row. Refresh is scheduled (cron), [[concepts/shedlock|ShedLock]]-coordinated across instances, with `startup-mode: BACKGROUND|BLOCKING|SKIP`.

## Related
- [[concepts/tsl-hot-swap-refresh]] · [[concepts/trusted-lists]] · [[concepts/tl-validation-job]]
- [[concepts/oj-keystore]] · [[concepts/hexagonal-architecture]]
