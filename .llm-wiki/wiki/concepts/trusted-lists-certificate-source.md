---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
---

# TrustedListsCertificateSource

The [[entities/dss]] `CertificateSource` implementation holding the trusted certificates extracted from EU [[concepts/trusted-lists|Trusted Lists]]. After a [[concepts/tsl-hot-swap-refresh]], a new instance is built and atomically swapped in as the live trust anchor for signature validation.

> Performance note (DSS docs §7): create a **single instance** and initialize it once, rather than rebuilding per verification — which is exactly why sign-verify-2 uses the hot-swap pattern.

## Related
- [[entities/dss]] · [[concepts/trusted-lists]] · [[concepts/tsl-hot-swap-refresh]]
- [[concepts/tl-validation-job]]
