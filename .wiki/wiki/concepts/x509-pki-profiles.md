---
type: concept
domain: engineering
created: 2026-06-28
updated: 2026-06-28
sources:
  - articles/2026-06-28-x509-rfc5280-certificati
volatility: warm
---

# X.509 PKI certificate and CRL profiles (RFC 5280, 6960, 5758)

## RFC 5280 — Certificate profile

The foundational standard for X.509 public key certificates used in eIDAS signature validation.

### Key extensions for digital signatures

**Key Usage (4.2.1.3)**
- `digitalSignature` (bit 0): verifying digital signatures (entity auth, data origin auth)
- `nonRepudiation`/`contentCommitment` (bit 1): non-repudiation service — protects against signing entity falsely denying action; a reliable third party can determine authenticity
- `keyCertSign` (bit 5): signing certificates (requires `cA=true`)
- `cRLSign` (bit 6): signing CRLs

Per DPCM 2013 Art. 28, qualified signing certificates **must** have `nonRepudiation`.

**Basic Constraints (4.2.1.9)**
- `cA`: TRUE for CA certificates, FALSE for end-entity
- `pathLenConstraint`: limits certification chain depth

**Subject Key Identifier / Authority Key Identifier**
- SKI: uniquely identifies the subject's public key
- AKI: identifies the issuer's key (essential for chain building)

**CRL Distribution Points (4.2.1.13)**
- URIs for CRL download locations
- Qualified certificates must include CDP (per DPCM 2013)

**Authority Information Access (AIA)**
- `id-ad-ocsp`: OCSP responder URI
- `id-ad-caIssuers`: CA certificate URI

### CRL profile (Section 5)
- Time-stamped list of revoked certificate serial numbers
- Signed by CA or CRL issuer
- Contains: issuer, thisUpdate, nextUpdate, revokedCertificates list
- Extensions: Authority Key Identifier, Issuer Alternative Name, CRL Number

## RFC 6960 — OCSP (Online Certificate Status Protocol)
- Real-time alternative to CRL fetching
- OCSPResponseStatus: successful, malformedRequest, internalError, tryLater, sigRequired, unauthorized
- CertStatus: good, revoked, unknown
- OCSP responses must be signed
- `producedAt`, `thisUpdate`, `nextUpdate` for freshness

## RFC 5758 — Additional Algorithms
Defines OIDs for ECDSA with SHA-256/384/512 used in qualified certificates and CAdES/XAdES/PAdES signatures.

## Related
- [[entities/eidas-regulation]] · [[concepts/trusted-lists]]
- [[concepts/revocation-data]] · [[concepts/signature-validation]]
- [[concepts/etsi-ades-formats]]
