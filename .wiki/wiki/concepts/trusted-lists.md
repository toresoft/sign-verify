---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-004
  - articles/2026-06-28-trusted-lists-lotl-tsl-struttura
---

# Trusted Lists (TL / LOTL)

The EU mechanism for establishing **trust anchors** for signature validation under [[entities/eidas-regulation]]. Each member state publishes a Trusted List (TL); the EU publishes the **List of Trusted Lists (LOTL)** that points to all national TLs (with **pivot** indirection supported).

## LOTL
- Published by the European Commission: `https://ec.europa.eu/tools/lotl/eu-lotl.xml`
- Contains pointers to all national Trusted Lists: X509Certificate of TL signer, TSLLocation URL, MIME type, scheme name
- Signed with a qualified electronic signature or seal
- Authenticated via digest published in the **Official Journal of the EU** (OJ keystore)

## National TL format (ETSI TS 119 612)
XML structure with:

1. **Scheme information**: TSLTag, TSLSequenceNumber, ListIssueDateTime, NextUpdate, SchemeTerritory
2. **TSP information**: name, address, TSPInformationURI
3. **Service information** per service:
   - **ServiceTypeIdentifier**: e.g. `CA/QC` (CA issuing QCs), `TSA` (time-stamping), `QCertESig` (QC for signature), `QCertESeal`, `QESig`, `QESeal`, `QVal` (qualified validation), `QPres` (preservation), `EAA` (attributes)
   - **ServiceName**
   - **ServiceDigitalIdentity**: X509Certificate + X509SKI
   - **ServiceStatus**: `granted` (active), `withdrawn` (status removed), `setbymutualagreement`, `deprecatedbymutualagreement`, `undersupervision`, `accredited`, `historical`
   - **StatusStartingTime**
   - **ServiceInformationExtensions**: QCStatement, Qualifications (Sie:Q:QcStatement, Sie:Q:NotQualified)
4. **Service history**: chronological status changes for auditing

## TLv6 (ETSI TS 119 612 v2.3.1/v2.4.1)
- Applicable from 29 April 2026 per Commission Implementing Decision **2025/2164**
- New ServiceTypeIdentifier values
- Better EAA support
- Updated XSD schema
- LOTL carries `TSLVersionIdentifier=6`

## Italian Trusted List (TSL-IT)
- URL: `https://eidas.agid.gov.it/TL/TSL-IT.xml`
- Scheme operator: AgID
- TL signing certificates: 5th and 6th certificate (GU n. 291, 16/12/2025)

## In DSS / sign-verify-2
- `TLValidationJob` + `LOTLSource`/`TLSource` + `TrustedListsCertificateSource` form the loading pipeline ([[entities/dss]] §11).
- sign-verify-2 performs [[concepts/tsl-hot-swap-refresh]]: refresh into a new source, then atomically swap the live `TrustedListsCertificateSource`; history rows in `tsl_refresh` (`SUCCESS`/`PARTIAL`/`FAILED`, cert add/remove/unchanged counts).
- Refresh is scheduled (cron), ShedLock-coordinated across instances, with `startup-mode: BACKGROUND|BLOCKING|SKIP`.
- A queryable `trusted_certificate` mirror is populated after refresh.

## Qualification determination
The qualified status of a certificate is determined by combining:
- ServiceTypeIdentifier (is it a QC service?)
- ServiceStatus (is it `granted` at the relevant time?)
- Certificate QCStatements (QcCompliance, QcSSCD)
If the CA/QC entry has status `withdrawn`, previously issued QCs **can no longer be validated as qualified** from the withdrawal date.

## Common failure
National TSLs failing with `PKIX path building failed` usually mean a **stale/missing OJ keystore** or a pivoted LOTL URL — rebuild the keystore when a new Official Journal is published ([[sources/SRC-2026-06-27-004]] §3.7).

## Related
- [[entities/dss]] · [[concepts/tsl-hot-swap-refresh]] · [[concepts/oj-keystore]]
- [[entities/eidas-regulation]] · [[entities/sign-verify-2]] · [[concepts/shedlock]]
