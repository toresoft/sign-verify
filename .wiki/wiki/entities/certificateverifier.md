---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
---

# CertificateVerifier

The [[entities/dss]] component that holds the trust/revocation configuration driving both validation and augmentation. `CommonCertificateVerifier` is the standard implementation. Configure it with:
- **revocation sources** — `setCrlSource(new OnlineCRLSource())`, `setOcspSource(new OnlineOCSPSource())` ([[concepts/revocation-data]]).
- **trusted cert sources** — `setTrustedCertSources(...)`, typically a [[concepts/trusted-lists-certificate-source|TrustedListsCertificateSource]] populated from the [[concepts/trusted-lists|Trusted Lists]].

Passed to `SignedDocumentValidator.setCertificateVerifier(cv)` for validation and to the `*AdESService` for augmentation to B/T/LT/LTA levels ([[concepts/baseline-profiles]]).

## Related
- [[entities/dss]] · [[entities/signeddocumentvalidator]]
- [[concepts/revocation-data]] · [[concepts/trusted-lists]]
