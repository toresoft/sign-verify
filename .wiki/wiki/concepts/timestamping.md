---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
---

# Timestamping (marcatura temporale)

Binding a piece of data to a trusted instant using a **Time-Stamping Authority (TSA)** per **RFC 3161**. A timestamp token is a CMS `TimeStampToken` signed by the TSA over `hash(data) || time || TSA-identity`; verification means checking the TSA's certificate chain/revocation (via [[concepts/trusted-lists|Trusted Lists]]) and that the imprint covers the claimed data.

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