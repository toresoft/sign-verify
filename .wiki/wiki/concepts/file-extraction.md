---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-27-006
  - sources/SRC-2026-06-27-002
volatility: warm
---

# File extraction

The sign-verify-2 capability that retrieves the **original unsigned document(s)** embedded inside a signed file, via the `ExtractionPort` (`DssExtractionAdapter`).

RFC 5544 TimeStampedData (`.tsd`) support is provided by [[entities/tsdawareextractionadapter|TsdAwareExtractionAdapter]], a decorator that unwraps the TSD wrapper via Bouncy Castle before falling through to the DSS delegate.

## Behaviour
- Endpoint `POST /api/v1/extractions` (multipart, `file` required).
- **Single original** → returned directly as binary with its MIME type + `Content-Disposition: attachment`.
- **Multiple originals** (typical of [[concepts/ades-signature-formats|ASiC-E]]) → packed into a ZIP (`application/zip`, `originals.zip`).
- Headers always present: `X-Signature-Format` (e.g. `CAdES`, `ASiC-E`), `X-Document-Count`.

## Lessons (2026-07-01 recursive-unwrap update)
- **`TsdAwareExtractionAdapter` was renamed `RecursiveExtractionAdapter`** and generalised from TSD-only to a recursive driver that unwraps ANY nested signed container (TSD → p7m/CAdES → … → leaf). Article [[entities/tsdawareextractionadapter]] is stale pending recompile.
- **Termination pattern:** recursion in a private helper (keeps `@CircuitBreaker` on the public entry only); a parse failure at `depth==0` propagates (400), at `depth>0` becomes a raw content-sniffed leaf; hard `MAX_DEPTH=10` bound. `X-Signature-Format` reports the OUTERMOST container. See [[2026-07-01-ll-extraction-recursive-unwrap]] L4.
- **Filename is now optional:** when the multipart filename is absent, a magic-byte `ContentTypeDetector` deduces a `document<ext>` name (the `file` part itself stays required — no OpenAPI change).

## Related
- [[entities/sign-verify-2]] · [[entities/dss]] · [[concepts/ades-signature-formats]]
- [[entities/tsdawareextractionadapter]] · [[concepts/rfc5544-tsd]]
- [[concepts/hexagonal-architecture]] (ExtractionPort / DssExtractionAdapter)
