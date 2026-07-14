---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
volatility: warm
---

# Revocation data (CRL / OCSP)

The mechanisms for checking whether a signing certificate has been revoked, central to [[concepts/signature-validation]]. [[entities/dss]] makes these pluggable via **sources** attached to the [[entities/certificateverifier|CertificateVerifier]].

## Sources
- **CRLSource** — Certificate Revocation Lists (e.g. `OnlineCRLSource`).
- **OCSPSource** — Online Certificate Status Protocol (e.g. `OnlineOCSPSource`).

DSS supports caching, offline fetching, and a revocation-data loading strategy + verifier (docs §6). Revocation evidence is embedded at LT/LTA levels (`RevocationValues`) to preserve long-term verifiability ([[concepts/baseline-profiles]]).

## In sign-verify-2
The [[entities/certificateverifier|CertificateVerifier]] configured by [[entities/dssvalidatoradapter|DssValidatorAdapter]] carries these sources; fetch failures contribute to the DSS [[concepts/circuit-breaker|circuit breaker]] opening.

- **Per-call HTTP timeouts live on the injected `DataLoader`, not a global setting.** No-arg `OnlineOCSPSource` / `OnlineCRLSource` / `DefaultAIASource` each build their own *unbounded* default `CommonsDataLoader`; a dead OCSP endpoint then burns long connect timeouts serially per cert per signature (minutes for a multi-signature document). Inject one `CommonsDataLoader` configured with `setTimeoutConnection`/`setTimeoutConnectionRequest`/`setTimeoutResponse`/`setTimeoutSocket` (DSS 6.4, int ms) into all three sources (and the TSL loader). Unreachable revocation still degrades to INDETERMINATE (correct eIDAS), it just fails fast. (From [[raw/notes/2026-07-14-ll-async-job-race-budget-phase]].)

## Related
- [[entities/dss]] · [[entities/certificateverifier]]
- [[concepts/signature-validation]] · [[concepts/baseline-profiles]]
