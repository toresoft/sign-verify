---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
volatility: warm
---

# CertificateVerifier

The [[entities/dss]] component that holds the trust/revocation configuration driving both validation and augmentation. `CommonCertificateVerifier` is the standard implementation. Configure it with:
- **revocation sources** — `setCrlSource(new OnlineCRLSource())`, `setOcspSource(new OnlineOCSPSource())` ([[concepts/revocation-data]]).
- **trusted cert sources** — `setTrustedCertSources(...)`, typically a [[concepts/trusted-lists-certificate-source|TrustedListsCertificateSource]] populated from the [[concepts/trusted-lists|Trusted Lists]].

Passed to `SignedDocumentValidator.setCertificateVerifier(cv)` for validation and to the `*AdESService` for augmentation to B/T/LT/LTA levels ([[concepts/baseline-profiles]]).

## Gotcha: not network-free by default (AIA)
A fresh `CommonCertificateVerifier` **seeds a `DefaultAIASource` even if you never call `setAIASource(...)`**, so it performs AIA (Authority Information Access) issuer-certificate fetches over the network independently of revocation. To run validation fully offline (e.g. tests without trust anchors) you must null all three sources explicitly: `setAIASource(null)`, `setOcspSource(null)`, `setCrlSource(null)` (and `setRevocationFallback(false)`). Dropping only OCSP/CRL is insufficient — AIA was the dominant network cost that made the offline SiVa corpus suite take ~717s vs ~40s once AIA was disabled. Getters to verify wiring: `getAIASource()/getOcspSource()/getCrlSource()/isRevocationFallback()`. — see [[sources/ll-dss-offline-test-verifier]]

## Related
- [[entities/dss]] · [[entities/signeddocumentvalidator]]
- [[concepts/revocation-data]] · [[concepts/trusted-lists]]
