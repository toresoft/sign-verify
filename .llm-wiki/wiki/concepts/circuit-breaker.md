---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
---

# Circuit breaker (DSS)

The [[entities/sign-verify-2]] resilience pattern (Resilience4j) wrapping the [[entities/dss]] validator. When DSS calls repeatedly fail (e.g. TSL/OCSP/CRL fetch failures), the breaker **opens** and the validation/async workers **skip their cycles** rather than pile up failing calls.

## Behaviour
- **CLOSED** → calls pass through normally.
- **OPEN** → calls fail-fast; `ValidationWorker` skips picking up [[concepts/async-verification-jobs|async jobs]] for the cycle; sync verifications get a fast failure instead of a hang.
- **HALF_OPEN** → a probe call tests whether the downstream has recovered; success re-closes, failure re-opens.

## Why here
DSS validation is expensive and depends on external network resources ([[concepts/trusted-lists]], OCSP/CRL). Without a breaker, a slow/dead dependency would exhaust the sync semaphore and async worker pool. The breaker complements the sync concurrency limit (429) and async back-pressure.

## Related
- [[entities/sign-verify-2]] · [[entities/dss]] · [[concepts/async-verification-jobs]]
- [[concepts/hexagonal-architecture]]
