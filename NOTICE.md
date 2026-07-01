# NOTICE

`sign-verify-2` original source code is licensed under the **GNU Lesser General Public
License v3.0 (LGPL-3.0)** — see [`LICENSE`](LICENSE).

This project depends on and bundles material from third-party projects under their own
licenses, listed below.

## Runtime dependency

### DSS — Digital Signature Services

- **Project:** [esig/dss](https://github.com/esig/dss) (`eu.europa.ec.joinup.sd-dss`), European
  Commission (DIGIT).
- **License:** GNU Lesser General Public License v2.1 (LGPL-2.1).
- **Use:** consumed as an ordinary Maven dependency (unmodified binary artifacts); not embedded
  or statically linked into `sign-verify-2` sources.

## Test fixtures

Test-only, non-distributed-at-runtime signed-document fixtures under `src/test/resources/assets/`.
Full inventory and caveats: [`src/test/resources/assets/README.md`](src/test/resources/assets/README.md).

| Source directory | Upstream | License | Notes |
|---|---|---|---|
| `assets/pades/`, `assets/cades/` | [esig/dss](https://github.com/esig/dss) `dss-pades`/`dss-cades` test resources | LGPL-2.1 | Unmodified test vectors, bundled with attribution |
| `assets/siva/` | [open-eid/SiVa-Test](https://github.com/open-eid/SiVa-Test) | EUPL-1.1 (copyleft) | See [`assets/siva/README.md`](src/test/resources/assets/siva/README.md); review EUPL-1.1 redistribution terms before publishing externally |
| `assets/tsd/` | CPAN `Crypt::TimestampedData`, FreeTSA token | GPL/Artistic (Perl dual license) | See [`assets/tsd/sample-rfc5544.tsd.README.md`](src/test/resources/assets/tsd/sample-rfc5544.tsd.README.md) |

None of the `src/test/resources/assets/` fixtures are packaged into the built application
artifact (jar/image); they are used exclusively at test time.
