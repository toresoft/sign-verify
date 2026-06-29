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

# trusted_certificate (table)

The queryable **trusted-certificate mirror** populated in [[entities/sign-verify-2]] after each [[concepts/tsl-hot-swap-refresh]]. Unlike the raw DSS source, it is queryable via the **Tsl** API: list/detail by subject, country, qualifiers, service status, `last_seen_at`.

Populated from the [[concepts/trusted-lists-certificate-source|TrustedListsCertificateSource]] extracted by [[entities/dsstsladapter|DssTslAdapter]] from the EU [[concepts/trusted-lists|LOTL/TL]]. Certificates no longer present after a refresh are tombstoned (`removed_at`) rather than hard-deleted.

## Related
- [[concepts/trusted-lists]] · [[concepts/tsl-hot-swap-refresh]]
- [[entities/tsl_refresh]] · [[entities/dsstsladapter]]
