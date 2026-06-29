---
type: source
title: "DSS 6.4 source code: CMSDocumentAnalyzer, DetachedTimestampAnalyzer, DSSUtils.isTimestampToken"
slug: dss-format-detection-research
status: ingested
created: 2026-06-27
updated: 2026-06-27
category: engineering
urls:
  - https://github.com/esig/dss/blob/master/dss-cades/src/main/java/eu/europa/esig/dss/cades/validation/CMSDocumentAnalyzer.java
  - https://github.com/esig/dss/blob/master/dss-validation/src/main/java/eu/europa/esig/dss/validation/timestamp/DetachedTimestampAnalyzer.java
  - https://github.com/esig/dss/blob/master/dss-spi/src/main/java/eu/europa/esig/dss/spi/DSSUtils.java
credibility: high
volatility: warm
---

# DSS Format Detection — Source Code

## Meccanismo ServiceLoader

`SignedDocumentValidator.fromDocument()` usa `ServiceLoader<DocumentValidatorFactory>`. Prima factory con `isSupported() == true` vince.

## CMSDocumentAnalyzer.isSupported()

```java
byte firstByte = DSSUtils.readFirstByte(doc);
if (DSSASN1Utils.isASN1SequenceTag(firstByte)) {  // 0x30
    return !DSSUtils.isTimestampToken(doc) && !EvidenceRecordAnalyzerFactory.isSupportedDocument(doc);
}
return false;
```

## DetachedTimestampAnalyzer.isSupported()

```java
byte firstByte = DSSUtils.readFirstByte(doc);
if (DSSASN1Utils.isASN1SequenceTag(firstByte)) {
    return DSSUtils.isTimestampToken(doc);  // opposto di CMS
}
return false;
```

## DSSUtils.isTimestampToken() — pivot method

Controlla `encapContentInfo.eContentType == id-ct-TSTInfo` (OID `1.2.840.113549.1.9.16.1.4`).
- `.tsr`: OID = TSTInfo → true → DetachedTimestampAnalyzer
- `.p7m` CAdES: OID = id-data → false → CMSDocumentAnalyzer
- RFC 5544 TSD: `contentType = id-aa-timeStampedData` (non SignedData) → CMSSignedDataParser exception → swallowed → false → CMSDocumentAnalyzer (poi buildSignatures() fallisce?)

## Magic bytes per formato

| Formato | Byte | Check |
|---|---|---|
| ASiC | 0x50 0x4B (ZIP) | Controlla mimetype entry |
| PDF | %PDF- | Struttura PDF |
| XML/XAdES | 0x3C | Parse XML |
| CAdES | 0x30 | !isTimestampToken() |
| TSR | 0x30 | isTimestampToken()==true |
| JAdES | 0x7B/0x5B | Parse JSON |

MIME type: ignorato dal routing (content-only detection).
