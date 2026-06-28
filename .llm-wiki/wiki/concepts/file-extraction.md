---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-006
  - sources/SRC-2026-06-27-002
---

# File extraction

The sign-verify-2 capability that retrieves the **original unsigned document(s)** embedded inside a signed file, via the `ExtractionPort` (`DssExtractionAdapter`).

## Behaviour
- Endpoint `POST /api/v1/extractions` (multipart, `file` required).
- **Single original** → returned directly as binary with its MIME type + `Content-Disposition: attachment`.
- **Multiple originals** (typical of [[concepts/ades-signature-formats|ASiC-E]]) → packed into a ZIP (`application/zip`, `originals.zip`).
- Headers always present: `X-Signature-Format` (e.g. `CAdES`, `ASiC-E`), `X-Document-Count`.

## Related
- [[entities/sign-verify-2]] · [[entities/dss]] · [[concepts/ades-signature-formats]]
- [[concepts/hexagonal-architecture]] (ExtractionPort / DssExtractionAdapter)
