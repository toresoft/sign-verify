---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/dss-format-detection-research
volatility: warm
---

# DSS Format Detection — Come fromDocument() instrada i formati

## Meccanismo: ServiceLoader

`SignedDocumentValidator.fromDocument(DSSDocument)` usa **Java `ServiceLoader<DocumentValidatorFactory>`**. Tutte le factory registrate sul classpath vengono interrogate con `factory.isSupported(doc)` — la prima che restituisce `true` gestisce il documento.

**Implicazione:** il riconoscimento del formato è modulare. Aggiungere un modulo DSS al classpath registra automaticamente nuovi formati.

## Magic byte per formato

| Formato | Primo byte (magic) | Check aggiuntivo |
|---|---|---|
| ASiC (.asics/.asice) | `0x50 0x4B` (ZIP) | Controlla entry `mimetype` dentro ZIP |
| PDF / PAdES | `%PDF-` (ASCII) | Struttura PDF |
| XML / XAdES | `0x3C` (`<`) | Parse XML |
| CAdES (.p7m) | `0x30` (ASN.1 SEQUENCE) | `!isTimestampToken()` |
| Timestamp puro (.tsr) | `0x30` (ASN.1 SEQUENCE) | `isTimestampToken() == true` |
| JAdES | `0x7B`/`0x5B` (JSON `{`/`[`) | Parse JSON |

## CAdES vs Timestamp puro: il pivot

### CMSDocumentAnalyzer.isSupported()

```java
public boolean isSupported(DSSDocument doc) {
    byte firstByte = DSSUtils.readFirstByte(doc);
    if (DSSASN1Utils.isASN1SequenceTag(firstByte)) {  // == 0x30
        return !DSSUtils.isTimestampToken(doc) &&
               !EvidenceRecordAnalyzerFactory.isSupportedDocument(doc);
    }
    return false;
}
```

### DetachedTimestampAnalyzer.isSupported()

```java
public boolean isSupported(DSSDocument doc) {
    byte firstByte = DSSUtils.readFirstByte(doc);
    if (DSSASN1Utils.isASN1SequenceTag(firstByte)) {
        return DSSUtils.isTimestampToken(doc);  // opposto di CMSDocumentAnalyzer
    }
    return false;
}
```

### DSSUtils.isTimestampToken() — il metodo pivot

```java
// In dss-spi/DSSUtils.java
public static boolean isTimestampToken(DSSDocument doc) {
    try {
        CMSSignedDataParser parser = new CMSSignedDataParser(...);
        return PKCSObjectIdentifiers.id_ct_TSTInfo.getId()
            .equals(parser.getSignedContentTypeOID());
        // OID id-ct-TSTInfo = 1.2.840.113549.1.9.16.1.4
    } catch (Exception e) {
        return false;  // swallowed — fail-safe
    }
}
```

**Discriminatore:** OID `encapContentInfo.eContentType`:
- `.tsr` (TimeStampToken): `1.2.840.113549.1.9.16.1.4` → `isTimestampToken() = true` → `DetachedTimestampAnalyzer`
- `.p7m` CAdES: `1.2.840.113549.1.7.1` (id-data) o altri → `isTimestampToken() = false` → `CMSDocumentAnalyzer`

## Check runtime: .tsd vs .tsr

Per sapere a runtime se un documento è un timestamp puro o un CAdES:

```java
boolean isPureTimestamp = DSSUtils.isTimestampToken(dssDocument);
// true  → .tsr / TimeStampToken puro → DetachedTimestampValidator
// false → .p7m / CAdES → SignedDocumentValidator.fromDocument()

// Oppure dopo routing:
AbstractDocumentValidator v = (AbstractDocumentValidator) SignedDocumentValidator.fromDocument(doc);
boolean isPureTs = v instanceof DetachedTimestampValidator;
```

## MIME type: ignorato dal routing

La detection è **puramente content-based**. `application/pkcs7-mime` vs `application/timestamp-reply` vs altri non influenzano `fromDocument()`. I MIME type compaiono solo come metadati nei manifest ASiC dopo che il container è già stato identificato come ZIP.

## RFC 5544 TSD — comportamento non documentato

Un file RFC 5544 TSD (`ContentInfo.contentType = id-aa-timeStampedData`, OID `1.2.840.113549.1.9.16.1.31`) non è `id-signedData`:
- `CMSSignedDataParser` fallisce sul parse → `isTimestampToken()` swallows exception → `false`
- `CMSDocumentAnalyzer.isSupported()` restituisce `true` (perché `!false && !evidenceRecord`)
- Ma `buildSignatures()` fallisce perché non è un SignedData valido

**Comportamento DSS con RFC 5544 TSD puro è indeterminato** senza test empirico. Vedere [[concepts/rfc5544-tsd]].

## Related

- [[entities/signeddocumentvalidator]] — fromDocument() entry point
- [[entities/detachedtimestampvalidator]] — per .tsr puri
- [[concepts/rfc5544-tsd]] — ambiguità .tsd RFC 5544 vs CAdES
- [[analyses/verifica-file-tsd]] — caso pratico routing .tsd
