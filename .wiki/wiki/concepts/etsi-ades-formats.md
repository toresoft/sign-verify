---
type: concept
domain: engineering
created: 2026-06-28
updated: 2026-06-28
sources:
  - articles/2026-06-28-etsi-ades-formats-baseline
  - articles/2026-06-28-eidas-regolamento-910-2014-approfondimento
---

# ETSI AdES signature formats and baseline profiles

## Standard framework

The ETSI ESI (Electronic Signatures and Infrastructures) committee defines the technical standards for AdES (Advanced Electronic Signatures) under the [[entities/eidas-regulation]] framework.

### Format standards

| Format | ETSI Standard | Underlying tech | Typical use |
|--------|--------------|----------------|-------------|
| **CAdES** | EN 319 122-1/2 | CMS (RFC 5652) | Binary/detached signatures (`.p7m`) |
| **XAdES** | EN 319 132-1/2 | XML DSig (W3C) | XML document signing |
| **PAdES** | EN 319 142-1/2 | PDF (ISO 32000) | PDF document signing |
| **JAdES** | TS 119 182-1 | JWS (RFC 7515) | REST/JSON payloads |
| **ASiC** | EN 319 162-1/2 | ZIP container | Associated Signature Containers |

Each format has **Part 1** (building blocks + baseline signatures) and **Part 2** (extended signatures).

### Baseline profile levels (B/T/LT/LTA)

Defined in ETSI EN 319 102-1 and common across all formats:

| Level | Added component | Purpose |
|-------|----------------|---------|
| **B** (Baseline) | Signed attributes (signing time, cert digest) | Minimal interoperable signature |
| **T** (Timestamp) | Counter-signature timestamp (RFC 3161 token) | Proves signature existed at a point in time |
| **LT** (Long Term) | Certificate chain + revocation data (CRL/OCSP) | Verifiable after certificate expiry/revocation |
| **LTA** (Archive) | Archive timestamps (periodic renewal) | Long-term preservation |

### Validation process (ETSI EN 319 102-1)

Produces three possible outcomes:
- **TOTAL-PASSED**: signature technically valid
- **TOTAL-FAILED**: signature invalid (hash mismatch, crypto failure, revoked cert, etc.)
- **INDETERMINATE**: cannot determine validity (missing POE, no certificate chain, crypto constraints, try later)

Sub-indications: `HASH_FAILURE`, `SIG_CRYPTO_FAILURE`, `REVOKED`, `EXPIRED`, `NO_POE`, `REVOKED_NO_POE`, `OUT_OF_BOUNDS_NO_POE`, `TRY_LATER`, `NO_CERTIFICATE_CHAIN_FOUND`, etc.

### Certificate profiles (ETSI EN 319 412)

- **412-2**: Qualified certificate for natural persons — `nonRepudiation` key usage, QCStatements
- **412-3**: Certificate for TSA — `id-kp-timeStamping` extended key usage
- **412-5**: QCStatements: `QcCompliance`, `QcSSCD`, `QcLegislationCountryCodes`, `QcType`

### Cryptographic suites (ETSI TS 119 312)
Defines acceptable algorithms and their operational expiration dates (e.g. RSA 2048 → 2030, SHA-256 → 2030). Referenced by DSS policy validation block `<AlgoExpirationDate>`.

### ETSI TS 119 322
Cryptographic Suite in XML/JSON format, used by DSS 6.x as alternative to the `<Cryptographic>` block within the validation policy.

## Related
- [[concepts/ades-signature-formats]] · [[concepts/baseline-profiles]]
- [[concepts/signature-validation]] · [[entities/eidas-regulation]]
- [[concepts/trusted-lists]] · [[concepts/timestamping]]
