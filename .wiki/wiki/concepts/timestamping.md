---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-27-001
  - articles/2026-06-28-time-stamping-regolamentazione
---

# Timestamping (marcatura temporale)

Binding a piece of data to a trusted instant using a **Time-Stamping Authority (TSA)** per **RFC 3161**. A timestamp token is a CMS `TimeStampToken` signed by the TSA over `hash(data) || time || TSA-identity`; verification means checking the TSA's certificate chain/revocation (via [[concepts/trusted-lists|Trusted Lists]]) and that the imprint covers the claimed data.

## Regulatory framework

### eIDAS Art. 41 — Presumption of accuracy
A **qualified electronic time stamp** enjoys the legal presumption of:
- Accuracy of the date and time it indicates
- Integrity of the data to which date/time are bound
This presumption operates automatically across all EU member states — the challenger bears the burden of proof.

### eIDAS Art. 42 — Requirements for qualified time stamps
Must: (a) bind date/time to data precluding undetectable changes; (b) use an accurate UTC-linked time source; (c) be signed with an advanced electronic signature/seal of the QTSP.

### Regulation (EU) 2025/1929
Implements Art. 42(2) by referencing ETSI EN 319 421 and EN 319 422 as standards for compliance presumption.

## ETSI standards

### ETSI EN 319 421 — Policy and security requirements for TSAs
- **TIS-7.7.1-01**: Time stamps must follow the EN 319 422 profile
- **TIS-8.1-01**: If qualified, TSU key certificate must use NCP+ policy (EN 319 411-1)
- **TIS-8.2-01**: A TSU issuing qualified time stamps cannot also issue non-qualified ones
- Qualified status is determined via Trusted List (service type, `granted` status)

### ETSI EN 319 422 — Time-stamp token profile
- Profiles RFC 3161 with ESSCertIDv2 (RFC 5816)
- Qualified time stamps must include `qcStatements` extension with `esi4-qtstStatement-1` (OID: id-etsi-tsts-EuQCompliance)
- `qcStatements` must NOT be marked critical
- Policy field in TSTInfo must reference EN 319 421

## TSA/TSU terminology
- **TSA**: Trust Service Provider offering time-stamping services
- **TSU (Time-Stamping Unit)**: technical server that creates and signs time stamps on behalf of the TSA
- Each time stamp identifies the responsible TSA through its signature

## In the signature stack [[entities/dss]]
- Timestamps are the DSS `Token` family's `TimestampToken`; validators surface them via `TokenExtractionStrategy.EXTRACT_TIMESTAMPS_ONLY` (docs §7.4.6).
- Standalone/detached timestamp **files** (`.tsd` self-contained data+token, `.tsr` detached token needing the original) are validated with [[entities/detachedtimestampvalidator|DetachedTimestampValidator]], *not* an entity-signature validator.
- Embedded timestamps drive the [[concepts/baseline-profiles|baseline profile]] levels: **B → T** (add a timestamp), **LT/LTA** (archive timestamps preserve long-term verifiability).

## The `.tsd` case

**Attenzione: il caso `.tsd` è più complesso di quanto sembra.**

Formalmente, `.tsd` = RFC 5544 `TimeStampedData` — un CMS container (`contentType = id-aa-timeStampedData`) che avvolge un documento + uno o più RFC 3161 token. NON è un CAdES `SignedData`.

In pratica, nella PA italiana i `.tsd` (da ArubaSign, GoSign free, Namirial) tipicamente CONTENGONO un `.p7m` CAdES all'interno. Quindi validare un `.tsd` richiede:
1. Parsare il wrapper RFC 5544 → estrarre il documento inner (`.p7m`)
2. Validare il `.p7m` inner come CAdES con `SignedDocumentValidator.fromDocument()`
3. Validare il timestamp token RFC 3161 dentro il wrapper

**Il comportamento di DSS `fromDocument()` su file RFC 5544 puri è da verificare empiricamente** — potrebbe non riconoscerli come CAdES. Vedi [[analyses/verifica-file-tsd|Verifica firma per file .tsd]] e [[concepts/rfc5544-tsd]].

## Related
- [[entities/detachedtimestampvalidator]] · [[entities/dss]]
- [[concepts/baseline-profiles]] · [[concepts/signature-validation]] · [[concepts/trusted-lists]]
- [[entities/eidas-regulation]] (Art. 41, 42)