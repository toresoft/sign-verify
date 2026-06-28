# SiVa-Test fixtures (core formats)

Signed-document test fixtures imported from the Estonian **SiVa** signature-validation test suite.

## Provenance & license

- **Upstream:** https://github.com/open-eid/SiVa-Test (`src/test/resources/`), downloaded 2026-06-28.
- **License:** **EUPL-1.1** (European Union Public Licence). This is a **copyleft** licence, distinct
  from the LGPL-2.1 files under `../pades`, `../cades`. Bundled here unmodified as test vectors with
  attribution. Review EUPL-1.1 redistribution terms before publishing the repo externally.

## Scope

Core eIDAS formats only — `pdf` (PAdES), `asice` / `asics` (ASiC-E / ASiC-S), `cades` (CAdES),
`xades` (XAdES). Excluded from upstream: `bdoc`/`ddoc` (legacy Estonian formats), `large_files`
(63 MB stress fixtures), and test infrastructure (schemas, keystores, properties).

## Inventory

| Dir | Files | Format |
|---|---|---|
| `pdf/` | 42 | PAdES signed PDFs (valid, multi-sig, negative) |
| `asice/` | 30 | ASiC-E containers |
| `asics/` | 72 | ASiC-S containers (incl. timestamp-token cases) |
| `xades/` | 28 | XAdES (incl. `xades/test/` enveloping sample) |
| `cades/` | 8 | CAdES (`.p7m`/`.p7s`/`.sce`/`.scs`) |

Filenames are self-describing for negative cases (e.g. `*wrongDigestValue*`, `*invalid*`,
`*expired*`). See upstream repo for the per-file expected-result matrix.

Catalog & rationale: `.wiki/wiki/analyses/cades-pades-test-corpus.md`.
