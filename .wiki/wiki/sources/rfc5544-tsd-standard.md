---
type: source
title: "RFC 5544 + RFC 5955: definizione formale formato TSD e adozione italiana"
slug: rfc5544-tsd-standard
status: ingested
created: 2026-06-27
updated: 2026-06-27
category: standards
urls:
  - https://www.rfc-editor.org/rfc/rfc5544.html
  - https://datatracker.ietf.org/doc/html/rfc5955
credibility: high
---

# RFC 5544 + RFC 5955 — Formato TSD

## Cosa dicono

**RFC 5544** (febbraio 2010, Adriano Santoni, Actalis S.p.A., Milano — Independent Submission IETF):
- Definisce `TimeStampedData` come CMS `ContentInfo` con `contentType = id-aa-timeStampedData` (OID `1.2.840.113549.1.9.16.1.31`)
- Struttura: `version`, `content` (documento originale, OCTET STRING), `temporalEvidence` (SEQUENCE di RFC 3161 TimeStampToken)
- Supporta catene di timestamp (`SEQUENCE SIZE(1..MAX)`) per long-term preservation

**RFC 5955** (agosto 2010, stesso autore):
- Registra MIME type `application/timestamped-data`
- Raccomanda esplicitamente estensione `.tsd`
- Questa è l'unica fonte formale che assegna l'estensione `.tsd`

## Distinzione critica

TSD (RFC 5544) **NON è CAdES**:
- CAdES: `ContentInfo.contentType = id-signedData` — firma digitale con timestamp come unsigned attribute
- TSD: `ContentInfo.contentType = id-aa-timeStampedData` — solo timestamp esterno attorno a qualsiasi contenuto

Namirial: "Firma e Marca" → CAdES-T (`.p7m`); "Marca sola" → TSD (`.tsd`). Le due cose NON coincidono.

## Normativa italiana

- CNIPA Deliberazione 45/2009 (G.U. 3 dicembre 2009): mandated TSD per TSA italiane
- DPCM 13 novembre 2014: regola validazione temporale (riconosce implicitamente TSD)
- GoSign free: TSD unico formato → più diffuso in PA

## Ambiguità per DSS

Un RFC 5544 TSD puro ha `contentType = id-aa-timeStampedData`. DSS `fromDocument()` via `CMSDocumentAnalyzer`: `isTimestampToken()` fallisce il parse (non è SignedData) → swallowed → returns false → `isSupported()` restituisce true. Ma `buildSignatures()` fallisce. Comportamento con file RFC 5544 puri non confermato da test empirico con DSS 6.4. Vedere [[concepts/rfc5544-tsd]].
