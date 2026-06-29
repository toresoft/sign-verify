---
type: concept
domain: standards
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/rfc5544-tsd-standard
  - sources/etsi-en-319-102-1-timestamp
volatility: warm
---

# RFC 5544 — Formato TSD (TimeStampedData)

## Definizione formale

**RFC 5544** ("Syntax for Binding Documents with Time-Stamps", febbraio 2010) di **Adriano Santoni, Actalis S.p.A., Milano** definisce la struttura `TimeStampedData`:

```asn1
TimeStampedData ::= SEQUENCE {
    version    INTEGER { v1(1) },
    dataUri    IA5String OPTIONAL,
    metaData   MetaData OPTIONAL,
    content    OCTET STRING OPTIONAL,
    temporalEvidence Evidence
}

Evidence ::= CHOICE {
    tstEvidence [0] TimeStampTokenEvidence
}

TimeStampTokenEvidence ::= SEQUENCE SIZE(1..MAX) OF TimeStampAndCRL
```

L'outer wrapper è un CMS `ContentInfo` con `contentType = id-aa-timeStampedData` (OID `1.2.840.113549.1.9.16.1.31`). **Non è un `id-signedData`**.

**RFC 5955** (agosto 2010, stesso autore) registra il MIME type `application/timestamped-data` e raccomanda l'estensione `.tsd`.

## Distinguo fondamentale: TSD vs CAdES-T

| | TSD (RFC 5544) | CAdES-T |
|---|---|---|
| Struttura outer | CMS `TimeStampedData` | CMS `SignedData` |
| Estensione tipica | `.tsd` | `.p7m` |
| Contiene firma? | No — solo timestamp(s) | Sì |
| Contenuto | Qualunque documento (anche `.p7m`) | Documento originale |
| Timestamp | Nell'`Evidence`, uno o più RFC 3161 token | Unsigned attribute `id-aa-signatureTimeStampToken` dentro il CAdES |
| Self-contained? | Sì (documento + token) | Sì (documento + firma + timestamp) |

Workflow comune in PA italiana: sign → `.p7m` (CAdES-B/T), poi timestamp → `.p7m.tsd` (TSD che incapsula il `.p7m`).

## Contesto normativo italiano

- **CNIPA Deliberazione 45/2009** (G.U. 3 dicembre 2009, efficace 31 agosto 2010) — mandated TSD come formato default per marca temporale nelle TSA italiane accreditate. Causa storica dell'adozione massiva in PA.
- **DPCM 13 novembre 2014** — "Regole tecniche su formazione, trasmissione e validazione temporale": riconosce implicitamente i formati usati dalle CA accreditate, incluso TSD.
- **DPCM 22 febbraio 2013** — regola CAdES/PAdES/XAdES per le firme (non il TSD, che è solo marca temporale).

## Adozione strumenti PA italiana

| Tool | TSD | TSR | CAdES-T (.p7m) |
|---|---|---|---|
| ArubaSign | ✓ | ✓ | ✓ (opzione T/LT/LTA) |
| GoSign (free) | ✓ (default) | — | — |
| GoSign Pro | ✓ | ✓ | — |
| Namirial FirmaCerta | ✓ (Marca sola) | ✓ | ✓ (Firma+Marca = CAdES-T) |
| DiKe/FirmaOK | ✓ | — | — |

GoSign free: TSD unico formato disponibile → è il formato più comune in PA.
Namirial: "Firma e Marca" → CAdES-T in `.p7m`; "Marca sola" → `.tsd` RFC 5544.

## DSS e RFC 5544 TSD — comportamento CONFERMATO da test empirico

Test `Rfc5544TsdRoutingTest` (2026-06-27) ha determinato definitivamente il comportamento:

```
DSSUtils.isTimestampToken(tsdDoc) → false
SignedDocumentValidator.fromDocument(tsdDoc) → throws:
  eu.europa.esig.dss.spi.exception.IllegalInputException:
  "A CMS file is expected : Not a valid CAdES file. Reason : Malformed content."
```

**DSS 6.4 NON supporta RFC 5544 TSD.** La sequenza:
1. `CMSDocumentAnalyzer.isSupported()`: byte `0x30` ✓, `isTimestampToken()` → exception swallowed → false, quindi `!false = true` → factory accetta
2. Il factory crea il validator, ma alla costruzione lancia `IllegalInputException` perché il documento non è un `SignedData` valido
3. `fromDocument()` propaga l'eccezione

**In `DssValidatorAdapter.validate()`** attuale: questa eccezione viene catturata e diventa `AppException.signatureParseError("cannot parse signed document: ...")`.

**Soluzione:** Parsing RFC 5544 con Bouncy Castle (bcpkix 1.84, già nel classpath) per estrarre l'inner `.p7m` prima di passare a DSS. Vedi [[analyses/verifica-file-tsd]] per il codice.

## Differenza con ASiC

| | TSD (RFC 5544) | ASiC-S / ASiC-E |
|---|---|---|
| Container | CMS/BER-DER | ZIP |
| Standard | RFC 5544 (IETF Indep.) | ETSI EN 319 162-1 |
| Adozione EU | Solo Italia (de-facto) | Obbligatorio in alcuni contesti (Dec. CE 2015/1506) |
| Anno | 2010 | 2011/2016 |

## Related

- [[analyses/verifica-file-tsd]] — analisi DSS routing per .tsd
- [[concepts/timestamping]] — marca temporale RFC 3161
- [[entities/detachedtimestampvalidator]] — validator per timestamp puri
- [[entities/signeddocumentvalidator]] — validator CAdES
- [[concepts/ades-signature-formats]] — panoramica formati firma
- [[sources/ll-tsd-jackson-toolchain]] — L1 imprint resolution, L2 DSS routing decorator
