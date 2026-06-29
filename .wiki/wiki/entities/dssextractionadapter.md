---
type: entity
category: project
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-006
volatility: warm
---

# DssExtractionAdapter

The [[entities/sign-verify-2]] adapter implementing the `ExtractionPort` ([[concepts/hexagonal-architecture]]). It uses [[entities/dss]] to locate the original unsigned document(s) embedded in a signed file and infer the signature format, powering [[concepts/file-extraction|File extraction]] (`POST /api/v1/extractions`).

Single original → binary response with its MIME type; multiple originals (typical of ASiC-E) → ZIP. Sets `X-Signature-Format` and `X-Document-Count` headers.

## Related
- [[concepts/file-extraction]] · [[entities/dss]]
- [[concepts/hexagonal-architecture]] · [[concepts/ades-signature-formats]]
