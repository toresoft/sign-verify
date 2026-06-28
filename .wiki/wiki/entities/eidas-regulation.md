---
type: entity
category: tool
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
---

# eIDAS Regulation

**EU Regulation 910/2014** — the legal framework for electronic identification, trust services and electronic signatures in the European Union. It defines *who* may use electronic signatures/seals and in what context, and mandates cross-border interoperability via baseline profiles ([[concepts/baseline-profiles]]).

## Key points
- Sets the legal effect of electronic signatures, seals, and trust services across all EU member states.
- **Article 32** governs the *times* at which certificate status/qualification is evaluated — DSS computes qualification at **two times** (certificate issuance and signing/validation time), since qualification can evolve (e.g. a TSP can lose granted status).
- Implemented through Commission Implementing Decisions: **2015/1505** (trusted list formats), **2015/1506** (signature formats), and the ETSI baseline profile technical specs (TS 103 171/173/172/174).
- Defines qualified/advanced signatures and the trust anchor chain via national [[concepts/trusted-lists]] aggregated in the EU LOTL.

## How it shapes the codebase
[[entities/sign-verify-2]] exists to give eIDAS-compliant verification results; [[entities/dss]] implements the ETSI EN 319 102-1 validation process and the qualification logic of ETSI TS 119 615 that eIDAS Article 32 requires.

## Related
- [[entities/dss]] · [[concepts/trusted-lists]] · [[concepts/baseline-profiles]]
- [[concepts/signature-validation]] (ETSI EN 319 102-1 process)
