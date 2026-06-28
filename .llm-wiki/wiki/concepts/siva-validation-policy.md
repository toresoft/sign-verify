---
type: concept
category: policy
created: 2026-06-28
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-28-001
  - sources/siva-research
tags: [siva, policy, polv3, polv4, baseline-profile, eidas, trusted-lists]
confidence: high
summary: "SiVa validation policy POLv3/POLv4 — baseline-profile matrix, common constraints (crypto, trust anchors, revocation freshness, trusted signing time), container rules, and the XML constraint-file configuration. Estonian-local; not territory-neutral."
---

# SiVa validation policy (POLv3 / POLv4)

SiVa ships a **closed policy set** — no integrator-supplied arbitrary DSS constraint XML (contrast [[entities/sign-verify-2]] verification profiles with per-request policy overrides, [[concepts/validation-profiles]]). Two policies are live in v2/v3; `POLv1`/`POLv2` are obsolete. Source: [[sources/SRC-2026-06-28-001]] (`docs/siva3/appendix/validation_policy.md`).

> **Territory warning (normative):** the result encodes constraints specific to **Estonian legislation and local signing practice**. The policy **may not be suitable for signatures created in other territories**.

## POLv3 vs POLv4

| | POLv3 | POLv4 (default) |
|---|---|---|
| Legal level | QES / AdES-QC / AdES **all pass** (type-agnostic) | Qualified-only; AdES / AdES-QC **fail** (seals: AdES-QC and above pass) |
| Signer cert QC | qualified or non-qualified; unknown QC compliance accepted | **must be qualified**; QC info from Trusted Lists considered; unknown → rejected |
| SSCD/QSCD | may or may not comply; unknown accepted | Signatures: should comply, else **warning**; Seals: no requirement; unknown type → must comply; QSCD info from TL considered |
| DDOC carve-out | — | DIGIDOC-XML 1.0..1.3 assumed QC+QSCD if issued by **SK** and nonRepudiation bit set |

Both share **Common constraints** below.

## Baseline Profile matrix (format × level)

| Format | B | T | LT | LT_TM | LTA |
|---|---|---|---|---|---|
| BDOC | – | – | – | **✓** | – |
| PAdES | – | – | **✓** | – | **✓** |
| XAdES | – | – | **✓** | – | **✓** |
| CAdES | – | – | **✓** | – | **✓** |
| DIGIDOC-XML 1.0..1.3 (+hashcode) | – | – | – | **✓** | – |

`BASELINE_B`/`T`/`LT`/`LTA` follow ETSI baseline profiles (TS 103 171/172/173); `LT_TM` is the time-mark variant (BDOC/DDOC). See [[concepts/baseline-profiles]].

## Common constraints

- **X.509:** signer cert Key Usage must have `nonRepudiation` (contentCommitment) bit.
- **Crypto:** RSA and ECC supported; min key length **RSA ≥ 1024 bits**, **ECC ≥ 192 bits** (PAdES/XAdES/CAdES). SHA-1 in BDOC → weak-digest **warning** (not failure).
- **Trust anchors:** signature must contain trust-anchor cert + full chain. For XAdES/CAdES/PAdES → **EU Member State Trusted Lists** (`ec.europa.eu/tools/lotl/eu-lotl.xml`); for DDOC → **SK CA certs** in local config. See [[concepts/trusted-lists]], [[concepts/oj-keystore]].
- **Revocation data:** must be embedded (OCSP per [[rfc6960]] or CRL); **no extra revocation fetched at validation time**; Trust Anchor revocation: DDOC → not checked, others → checked via TL.
- **Revocation freshness (LT/LTA, time-stamp):** if `|timestamp genTime − OCSP producedAt| ≥ 15 min` → warning; for CRL, `genTime` must be within `[thisUpdate, nextUpdate]`. For `LT_TM` (time-mark) revocation is always fresh (issued at trusted signing time).
- **Trusted signing time:** `LT_TM` → earliest valid time-mark `producedAt`; `T`/`LT`/`LTA` → earliest valid timestamp `genTime`; `B` → none. QES with timestamp / ASiC-S TST → qualified timestamps only (TSA/QTST services).

## Container-specific rules

- **BDOC 2.1:** `.bdoc`; one signature per `signatures.xml`; all datafiles signed; relative paths only; case-insensitive names; `META-INF/manifest.xml` per ODF 1.0/1.2.
- **ASiCE (EN 319 162-1):** warning if not all datafiles signed; manifest **must** be present.
- **ASiCS (EN 319 162-1/-2):** signature- or TST-based (no evidence-record); manifest forbidden for signature-based; one datafile; one `META-INF/timestamp.tst` (extra timestamps → `timestamp001.tst` etc.); TST validated via DSS with TSL verification.

## Configuration (XML constraint files)

Policies are wired through two parallel property trees — **TimeMark (BDOC/DDOC)** and **Generic (EU)** — each pointing at a classpath/file DSS constraint XML:

```
siva.bdoc.signaturePolicy.defaultPolicy    = POLv4
siva.bdoc.signaturePolicy.policies[index].{name,description,url,constraintPath}
siva.europe.signaturePolicy.defaultPolicy  = POLv4
siva.europe.signaturePolicy.policies[index].{name,description,url,constraintPath}
```

Defaults: `bdoc_constraint_ades.xml` (POLv3) / `bdoc_constraint_qes.xml` (POLv4); `generic_constraint_ades.xml` / `generic_constraint_qes.xml`. **Overriding any policy detail drops the built-in defaults** — redefine explicitly. BDOC override file: `siva.bdoc.digidoc4JConfigurationFile` (default `classpath:/siva-digidoc4j.yaml`).

### T-level revocation filter

By default `T`-level signatures carry no revocation data. SiVa can be configured to use `OnlineOCSPSource` for T-level (and above) per country:

```
t-level-signature-filter.filter-type = ALLOWED_COUNTRIES   # or NOT_ALLOWED_COUNTRIES
t-level-signature-filter.countries[0] = LV
```

Applies **only to the Generic validator** (not BDOC/DDOC); empty `ALLOWED` list → no OCSP; empty `NOT_ALLOWED` → OCSP for every country.

## See also

[[entities/siva]] · [[concepts/siva-rest-interface]] · [[concepts/siva-report-schema]] · [[concepts/validation-profiles]] · [[concepts/baseline-profiles]] · [[concepts/trusted-lists]] · [[concepts/oj-keystore]] · [[concepts/revocation-data]] · [[concepts/etsi-en-319-102-1-validation]] · [[concepts/signature-qualification]]
