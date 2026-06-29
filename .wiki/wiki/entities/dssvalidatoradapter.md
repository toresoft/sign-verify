---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
volatility: warm
---

# DssValidatorAdapter

The [[entities/sign-verify-2]] adapter implementing the `SignatureValidatorPort` ([[concepts/hexagonal-architecture]]). It wraps [[entities/dss]]'s `SignedDocumentValidator`: configures a [[entities/certificateverifier|CertificateVerifier]] with [[concepts/trusted-lists-certificate-source|trusted certs]] and [[concepts/revocation-data|revocation sources]], runs validation against the selected [[concepts/validation-profiles|profile]]'s policy XML, and returns the requested [[concepts/reports|DSS Reports]].

Wrapped by a Resilience4j [[concepts/circuit-breaker|circuit breaker]] so repeated DSS failures fail fast instead of exhausting the sync semaphore / async worker pool.

## Related
- [[entities/dss]] · [[entities/signeddocumentvalidator]] · [[entities/certificateverifier]]
- [[concepts/hexagonal-architecture]] · [[concepts/circuit-breaker]] · [[concepts/validation-profiles]]
