# Test assets — signed-file fixtures

Functional-test fixtures (CAdES `.p7m`/`.p7s` and PAdES signed PDFs) for the verification pipeline.

## Provenance & license

All files in this directory are **test resources from the EU DSS library** (`esig/dss`),
downloaded 2026-06-28 from `dss-pades` / `dss-cades` `src/test/resources/validation/`.

- **Upstream:** https://github.com/esig/dss
- **License:** GNU **LGPL-2.1** (`SPDX-License-Identifier: LGPL-2.1`). Bundled here as unmodified
  test vectors with attribution.

## Caveats

- **Trust at validation time:** these files chain to EU LOTL CAs that may be **expired**. A correct
  validator can therefore return `INDETERMINATE` (missing POE), not `PASSED`. For deterministic
  `PASSED` assertions, pin the validation time/policy or generate fixtures against your own test CA.
- **Detached signature:** `cades/cades-bes-signeddata-detached.p7s` needs the ORIGINAL signed
  content to validate — not bundled here. Use it only for detached-routing/parse tests, or fetch the
  companion content from the DSS repo.
- `cades/malformed-cades.p7m` is intentionally malformed (negative fixture) — `openssl asn1parse`
  reports "Error in encoding"; that is expected.

## Inventory

### PAdES (`pades/`)
| File | Case | sha256 (prefix) |
|---|---|---|
| `pades-bes.pdf` | PAdES-B valid | `5ad09bb8` |
| `PAdES-LT.pdf` | PAdES-LT valid | `1e1ec1d3` |
| `PAdES-LTA.pdf` | PAdES-LTA valid (archive timestamp) | `876e78f4` |
| `pades-5-signatures-and-1-document-timestamp.pdf` | multi-signature + doc timestamp | `962dd614` |
| `modified_after_signature.pdf` | negative: modified after signing | `0e41604c` |
| `pdf-signed-corrupted.pdf` | negative: corrupted signature | `8be92f00` |
| `sample-pades-valid.pdf` | PAdES valid (pre-existing repo fixture, used by adapter/IT tests) | — |

### CAdES (`cades/`)
| File | Case | sha256 (prefix) |
|---|---|---|
| `cades-bes-signeddata-enveloping.p7m` | CAdES-B enveloping (PA `.p7m` style) | `aeb14ca4` |
| `Signature-CBp-LT-2.p7m` | CAdES-LT | `761b93d2` |
| `Signature-C-B-LTA-10.p7m` | CAdES-LTA | `a859f248` |
| `cades-bes-signeddata-detached.p7s` | CAdES-B detached (needs original) | `12b9e38d` |
| `malformed-cades.p7m` | negative: malformed | `06dfab34` |
| `cades-broken-sig-tst.p7m` | negative: broken signature timestamp | `2bbd41e2` |

### TSD (`tsd/`)
| File | Case | License |
|---|---|---|
| `sample-rfc5544.tsd` | RFC 5544 TimeStampedData (routing/parse) | Perl/GPL/Artistic (CPAN) |

`tsd/` has separate provenance (CPAN `Crypt::TimestampedData`, FreeTSA token) — see
`tsd/sample-rfc5544.tsd.README.md`.

### SiVa (`siva/`)
Core-format fixtures (PAdES/CAdES/XAdES/ASiC, 180 files) from the Estonian SiVa test suite —
**EUPL-1.1** (copyleft, differs from the LGPL-2.1 files here). See `siva/README.md`.
Catalog & rationale: `.llm-wiki/wiki/analyses/cades-pades-test-corpus.md`.
