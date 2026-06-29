# sample-rfc5544.tsd — provenance & license

RFC 5544 TimeStampedData (TSD) test fixture.

- **Origin:** `test_output.tsd` from the CPAN distribution
  [`Crypt::TimestampedData` 0.01-TRIAL](https://metacpan.org/release/BRUGNARA/Crypt-TimestampedData-0.01-TRIAL)
  by Guido Brugnara.
- **License:** same as Perl (GPL v1 / Artistic). Bundled here as a test vector with attribution.
- **SHA-256:** `da32e199ee37d81a9e82378d5b152abeb3e466530f0431fc9f43aefa8bd47226`

## Structure (verified with `openssl asn1parse`)

- `ContentInfo.contentType` = `1.2.840.113549.1.9.16.1.31` (id-ct-timestampedData) — RFC 5544.
- `TimeStampedData` v1, `metaData.fileName = test_data.txt`.
- `content` = embedded CMS **pkcs7-data** (plain content, NOT a CAdES `.p7m` signature).
- `temporalEvidence` = RFC 3161 token from **FreeTSA** (`www.freetsa.org`), SHA-512,
  produced 2025-09-27T13:20:41Z.

## Test scope

Use for the **routing/parse** path: a real RFC 5544 TSD that DSS 6.4
`SignedDocumentValidator.fromDocument()` rejects (`IllegalInputException`), exercising the
Bouncy Castle unwrap fallback.

⚠️ This TSD wraps unsigned content. It does NOT cover the Italian-PA case (TSD over a signed CAdES
`.p7m`). For that, generate a fixture with `CMSTimeStampedDataGenerator` over a test `.p7m`.
