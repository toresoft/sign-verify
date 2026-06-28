---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
---

# TLValidationJob

The [[entities/dss]] class that orchestrates [[concepts/trusted-lists|Trusted Lists]] loading: downloads LOTL/TL sources, verifies their signatures against the OJ keystore, extracts trusted services/certificates, and populates a `TrustedListsCertificateSource`. Drives the [[concepts/tsl-hot-swap-refresh]] pipeline in [[entities/sign-verify-2]] (implemented by the `DssTslAdapter`).

Configured with one or more `LOTLSource`/`TLSource` entries; supports pivot indirection and TL version filtering. See DSS docs §11 ([[sources/SRC-2026-06-27-001]]) and the trusted-certificates guide [[sources/SRC-2026-06-27-004]].

## Related
- [[entities/dss]] · [[concepts/trusted-lists]] · [[concepts/tsl-hot-swap-refresh]]
- [[concepts/trusted-lists-certificate-source]] · [[concepts/oj-keystore]]
