---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
---

# Revocation data (CRL / OCSP)

The mechanisms for checking whether a signing certificate has been revoked, central to [[concepts/signature-validation]]. [[entities/dss]] makes these pluggable via **sources** attached to the [[entities/certificate-verifier|CertificateVerifier]].

## Sources
- **CRLSource** — Certificate Revocation Lists (e.g. `OnlineCRLSource`).
- **OCSPSource** — Online Certificate Status Protocol (e.g. `OnlineOCSPSource`).

DSS supports caching, offline fetching, and a revocation-data loading strategy + verifier (docs §6). Revocation evidence is embedded at LT/LTA levels (`RevocationValues`) to preserve long-term verifiability ([[concepts/baseline-profiles]]).

## In sign-verify-2
The [[entities/certificate-verifier|CertificateVerifier]] configured by [[entities/dss-validator-adapter|DssValidatorAdapter]] carries these sources; fetch failures contribute to the DSS [[concepts/circuit-breaker|circuit breaker]] opening.

## Related
- [[entities/dss]] · [[entities/certificate-verifier]]
- [[concepts/signature-validation]] · [[concepts/baseline-profiles]]
