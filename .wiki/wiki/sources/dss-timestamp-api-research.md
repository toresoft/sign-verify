---
type: source
title: "DSS 6.4 Javadoc: TimestampWrapper, SignatureWrapper, SimpleReport API"
slug: dss-timestamp-api-research
status: ingested
created: 2026-06-27
updated: 2026-06-27
category: engineering
urls:
  - https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/apidocs/eu/europa/esig/dss/diagnostic/TimestampWrapper.html
  - https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/apidocs/eu/europa/esig/dss/diagnostic/SignatureWrapper.html
  - https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/apidocs/eu/europa/esig/dss/diagnostic/DiagnosticData.html
  - https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/apidocs/eu/europa/esig/dss/simplereport/SimpleReport.html
credibility: high
---

# DSS 6.4 API — Timestamp Extraction

## TimestampWrapper (chiave per DTO)

- `getType()` → `TimestampType` enum: CONTENT_TIMESTAMP, SIGNATURE_TIMESTAMP, ARCHIVE_TIMESTAMP, CONTAINER_TIMESTAMP, DOCUMENT_TIMESTAMP
- `getProductionTime()` → `Date` (ora TSA)
- `getTSAGeneralNameValue()` → `String` (nome TSA da TSTInfo)
- `isMessageImprintDataIntact()` → `boolean`
- `getArchiveTimestampType()` → per ARCHIVE_TIMESTAMP: v2/v3
- `isAtsHashIndexValid()` → per CAdES archive timestamp

## SignatureWrapper (per firma)

Named convenience methods: `getContentTimestamps()`, `getTLevelTimestamps()` (= SIGNATURE_TIMESTAMP), `getALevelTimestamps()` (= ARCHIVE_TIMESTAMP), `getTimestampList()` (tutti).

⚠️ **Trappola**: `DiagnosticData.getSignatures()` include counter-firme. Usare `getAllSignatures()` per soli master.

## SimpleReport (indication per DTO)

- `getIndication(tokenId)` — per signatureId E timestampId: `TOTAL_PASSED / INDETERMINATE / FAILED`
- `getSubIndication(tokenId)` — SubIndication granulare
- `getProducedBy(tsId)` — nome TSA da TrustedList (da SimpleReport, non da TSTInfo)
- `getProductionTime(tsId)` — ridondante con TimestampWrapper ma comodo
- `getTimestampQualification(tsId)` — `QTSA / TSA / NA`
- `getSignatureFormat(sigId)` — `CAdES_BASELINE_LT` etc.
- `getBestSignatureTime(sigId)` — tempo LT/LTA → preferire per scopi legali
- `getSigningTime(sigId)` — tempo dichiarato

## WSReportsDTO (REST ufficiale DSS)

`XmlTimestamp` (JAXB): eredita `id`, `indication`, `subIndication`, `certificateChain` da `XmlToken`; aggiunge `productionTime`, `producedBy`, `timestampLevel`.

`XmlSignature`: `id`, `signingTime`, `bestSignatureTime`, `signedBy`, `indication`, `subIndication`, `signatureFormat`, `timestamps`, `counterSignature`.

Algoritmo: NON in SimpleReport → `DiagnosticData.getTimestampById(id).getDigestAlgorithm()`.

## SiVa (Estonian DSS-based REST service)

Field names production-ready: `signedTime` (= `productionTime`), `signedBy` (= `producedBy`), `timestampLevel`, `signatureScopes[]`, `info.archiveTimeStamps[]`. Aggregazione: `validSignaturesCount == signaturesCount` (nessun campo `overallValid` esplicito).
