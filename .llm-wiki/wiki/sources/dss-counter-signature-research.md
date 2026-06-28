---
type: source
title: "DSS 6.4 source: CAdES counter-signature limitations e API"
slug: dss-counter-signature-research
status: ingested
created: 2026-06-27
updated: 2026-06-27
category: engineering
urls:
  - https://github.com/esig/dss/blob/master/dss-diagnostic-jaxb/src/main/java/eu/europa/esig/dss/diagnostic/DiagnosticData.java
  - https://github.com/esig/dss/blob/master/dss-cades/src/test/java/eu/europa/esig/dss/cades/signature/CAdESLevelBNestedCounterSignatureTest.java
  - https://github.com/esig/dss/blob/master/dss-cades/src/test/java/eu/europa/esig/dss/cades/signature/CAdESLevelLTACounterSignatureTest.java
  - https://github.com/esig/dss/blob/master/dss-cades/src/test/java/eu/europa/esig/dss/cades/signature/CAdESCounterSignSignaturesConsequentlyTest.java
credibility: high
---

# DSS Counter-Signature Research

## Limiti

- **Nesting > 1**: `UnsupportedOperationException("Nested counter signatures are not supported with CAdES!")` (a firma, non a validazione)
- **Livello counter-firma**: solo BASELINE-B ammesso (`UnsupportedOperationException` se si tenta T/LT/LTA)
- **B-level reference check**: vuoto (skipped) — counter firma bytes del master, non documento → check reference failure conosciuto

## API discriminazione master vs counter

```java
dd.getAllSignatures()           // solo master (parent == null)
dd.getAllCounterSignatures()    // solo counter
dd.getAllCounterSignaturesForMasterSignature(sig)
sig.isCounterSignature()        // flag
dd.getSignatures()              // INCLUDE counter → ⚠️ non usare per conti
```

## Conteggi esempio

2 firmatari paralleli + 2 counter-firme:
- `dd.getSignatures().size()` = 4 (SBAGLIATO per "quanti firmatari")
- `dd.getAllSignatures().size()` = 2 ✓
- `dd.getAllCounterSignatures().size()` = 2 ✓

## INDETERMINATE per counter B-level

Counter B-level: no timestamp proprio, no revocation data. DSS riusa store CMS del master per chain building. Senza POE dal master timestamp → `INDETERMINATE / NO_POE` o `OUT_OF_BOUNDS_NO_POE` per il certificato del controfimatario.

## Bug risolti rilevanti (DSS 6.x)

- DSS-3705 (fixed 6.3): counter-firma senza `Reference Type` attribute era ignorata silenziosamente
- DSS-3725 (fixed 6.4): validazione multipla ridondante per counter-firme XAdES/JAdES
