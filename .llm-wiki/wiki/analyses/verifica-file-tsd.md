---
type: analysis
created: 2026-06-27
updated: 2026-06-27
query: "implementazione verifica firma per file .tsd"
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-003
---

# Verifica firma per file `.tsd`

_Risposta di ricerca (deep) alla domanda: «implementazione verifica firma per file `.tsd`»._

> ⚠️ **Correzione (2026-06-27).** La prima stesura trattava `.tsd` come *timestamp detached puro* → `DetachedTimestampValidator`. Il caso operativo reale nella PA italiana è **file firmati marcati temporalmente** (CAdES tipo `.p7m` con timestamp embedded, spesso annidati): validare un `.tsd` significa **navigare tutti i contenitori/signature/timestamp annidati**, come per il `.p7m`. L'API di routing corretta è quindi **`SignedDocumentValidator.fromDocument()`** (CAdES), il quale gestisce signature + timestamp embedded + controfirme. `DetachedTimestampValidator` resta solo per il raro caso *timestamp nudo* (`.tsr`/CMS senza contenuto firmato). La sezione "Correzione" riassume il cambio.

## Cos'è un `.tsd` (caso reale)

Un `.tsd` ("Time-Stamped Data") è, nel caso operativo PA italiana (ArubaSign, GoSign), un **inviluppo CAdES/CMS (`ContentInfo`/`SignedData`) che trasporta firma(e) + marca(e) temporale(i) embedded** — tipicamente un CAdES-BASELINE-**T** (o LT/LTA), *esattamente come un `.p7m` marcat临时aneamente*. La verifica è quindi **identica a quella di un `.p7m` CAdES**: DSS deve **discendere tutti i contenitori/signature annidate**, validare ogni `AdvancedSignature`, ogni timestamp embedded, eventuali controfirme e archive-timestamp.

> Distinguere dall'uso *puntuale* "timestamp detached" (token `.tsr` / CMS nudo): lì sì serve [[entities/detachedtimestampvalidator|DetachedTimestampValidator]].

## API DSS di routing ( corretta )

```java
SignedDocumentValidator v = SignedDocumentValidator.fromDocument(doc); // autodetect CAdES
v.setCertificateVerifier(cv);
Reports reports = v.validateDocument();   // valida tutte le signature + timestamp embedded
List<AdvancedSignature> sigs = v.getSignatures(); // every (nested/counter) signature
```

DSS:
- **autodetect per contenuto** (CAdES via `dss-cades`), **non per estensione** — `.tsd` e `.p7m` sono entrambi `ContentInfo` CMS (docs §5, modalità ISO/PKCS#7).
- `validateDocument()` percorre **tutte** le firme del documento, comprese **controfirme** (CAdES: B-level counter, multiple — docs §4.8) e **timestamp embedded** (ArchiveTimeStamp a livello LT/LTA, constraint `ContainerSignedAndTimestampedFilesCovered`).
- Ogni firma/timestamp è un `Token` con esito; il [[concepts/reports|Reports]] aggrega indication/subIndication per ciascuno.

## Cosa implica per sign-verify-2 (correzione del piano)

1. **Nessun ramo dedicato per `.tsd`.** L'attuale `DssValidatorAdapter` che usa `SignedDocumentValidator.fromDocument()` **gestisce già** "firma marcata temporalmente" tramite autodetect CAdES. La pipeline di validation ([[concepts/validation-profiles|profili]] + [[concepts/trusted-lists|Trusted Lists]] + [[concepts/reports|Reports]]) è già corretta. **NonServe `DetachedTimestampAnalyzer` come pre-filter** nel caso comune.
2. **Il lavoro reale è nell'iterazione del risultato** (non nel routing):
   - `Reports` porta **multiple signatures**: surface `signatureCount` e — per i timestamp annidati — esporre anche `timestampCount` / i dettagli TSA (chain via Trusted Lists, revocation). La [[concepts/reports|forma SimpleReport]] elenca già ogni signature; per `.tsd` può servire arricchire il mapping DTO per rendere **esplicite le marche temporali embedded** (Tipo = archive/content timestamp, TSA, istante).
   - Indicazione aggregata: una firma marcata temporalmente con chain TSA non trusted → `INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` sulla **marca**, mentre la firma B-level può passare. Decidere la semantica di aggregazione (es. worst-of) coerente con [[concepts/signature-validation]].
3. **Caso speciale "timestamp nudo"** (raro, es. file `.tsr` o CMS puro senza SignedData firmata): qui serve davvero `DetachedTimestampAnalyzer` → [[entities/detachedtimestampvalidator|DetachedTimestampValidator]]. Sarà il `ExtractionPort`/un altro endpoint, non l'endpoint `/verifications` principale.
4. **MIME / encapsulation**: trattare come `application/pkcs7-mime` (CMS). DSS prova sul contenuto, quindi l'estensione è irrilevante a parte la convenzione PA.

## Piano di test (e2e, allineato al caso reale)
- Corpus `*.tsd` e `*.p7m` (CAdES-T/LT, anche **multi-firma e annidati**, es. sample Aruba/GoSign) → asserire `TOTAL_PASSED`, e che il report elenchi tutte le firme + tutte le marche temporali embedded.
- Negativo: TSA non in Trusted List → `INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` sulla marca; firma B-level ancora `PASSED` (verifica la semantica di aggregazione scelta).
- Controfirme (CAdES B-level counter) → validate e presenti nel report.

## Confronto `.tsd` vs `.tsr` (per chiarezza)
| | `.tsd` (caso PA) | `.tsr` |
|---|---|---|
| Contenuto | CMS CAdES: firma(e) + timestamp embedded | token RFC 3161 puro |
| Autocontenuto? | sì | no (serve dato originale per ricalcolare imprint) |
| Validator DSS | `SignedDocumentValidator.fromDocument()` | [[entities/detachedtimestampvalidator|DetachedTimestampValidator]] |
| Validare = | signature + timestamp embedded (walk completo) | solo marca temporale |

## ✅ Chiarimento definitivo RFC 5544 (test empirico 2026-06-27)

**Un file RFC 5544 TSD NON è gestibile da `SignedDocumentValidator.fromDocument()`.** Test `Rfc5544TsdRoutingTest` (file `src/test/…/adapter/dss/Rfc5544TsdRoutingTest.java`) ha confermato:

```
fromDocument() threw →
eu.europa.esig.dss.spi.exception.IllegalInputException:
  A CMS file is expected : Not a valid CAdES file. Reason : Malformed content.
```

**DSS 6.4 non ha factory per RFC 5544 TSD.** `CMSDocumentAnalyzer.isSupported()` restituisce `true` (byte `0x30`, `!isTimestampToken()` false per exception swallowed), ma poi il factory crea il validator che lancia `IllegalInputException` al build delle firme. Nel `DssValidatorAdapter` attuale questo diventa `AppException.signatureParseError("cannot parse signed document: ...")`.

**Implicazione per sign-verify-2:**

I file `.tsd` da ArubaSign/GoSign (RFC 5544) vengono RIFIUTATI dall'endpoint `/verifications` attuale. Serve supporto esplicito.

### Approccio implementativo per TSD support

```java
// In DssValidatorAdapter.validate() o in un nuovo TsdAwareValidatorAdapter:
try {
    validator = SignedDocumentValidator.fromDocument(doc);
} catch (IllegalInputException | UnsupportedOperationException e) {
    // Potrebbe essere RFC 5544 TSD — prova parsing Bouncy Castle
    try {
        CMSTimeStampedData tsd = new CMSTimeStampedData(new CMSProcessableByteArray(req.documentBytes()));
        byte[] innerBytes = tsd.getContent();  // il .p7m CAdES dentro
        if (innerBytes != null && innerBytes.length > 0) {
            var innerDoc = new InMemoryDocument(innerBytes, deriveInnerName(req.filename()));
            validator = SignedDocumentValidator.fromDocument(innerDoc);
            // TODO: validare anche i TimeStampToken del wrapper TSD via DetachedTimestampValidator
        } else {
            throw AppException.signatureParseError("TSD wrapper without inner document");
        }
    } catch (CMSException | IOException tsdEx) {
        throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
}
```

**Classi Bouncy Castle disponibili** (bcpkix 1.84, già nel classpath):
- `org.bouncycastle.tsp.cms.CMSTimeStampedData` — parse RFC 5544
- `CMSTimeStampedData.getContent()` → inner document bytes
- `CMSTimeStampedData.getTimeStampTokenEvidence()` → `TimeStampToken[]` — i token RFC 3161

**Verifica routing**:
- `DSSUtils.isTimestampToken(tsdDoc)` → **false** (confermato: RFC 5544 non è `id-ct-TSTInfo`)
- `SignedDocumentValidator.fromDocument(tsdDoc)` → **lancia `IllegalInputException`** (confermato)

Vedere [[concepts/rfc5544-tsd]] e [[concepts/dss-format-detection]] per i dettagli del meccanismo.

## Test vectors disponibili

**Non esistono corpus `.tsd` pubblici.** DSS GitHub (`esig/dss`):
- `dss-cades/src/test/resources/plugtest/cades/` — ha CAdES-BES, CAdES-T, CAdES-XL, CAdES-A come `.p7m`
- `plugtest/esig2014/ESIG-CAdES/` — `.csig` da ETSI Plugtests 2014
- **Zero file `.tsd`** nell'intero corpus DSS

ETSI Plugtests: materiali gated (registration required). AGID: zero campioni pubblici.

→ **Fonti per test corpus reali:** richiedere campioni direttamente ad ArubaSign/GoSign, o usare i tool per produrre file di test con dati sintetici.

## DTO mapping per il report

Vedi [[analyses/tsd-dto-mapping]] per la mappatura completa DSS Reports → JSON DTO.

API chiave: [[concepts/dss-timestamp-api]] (TimestampWrapper, SignatureWrapper, SimpleReport).
Algoritmo normativo: [[concepts/etsi-en-319-102-1-validation]] (§5.4-§5.6).

## Fonti
- DSS docs §7 (validation), §4.8 (counter signatures), §5 (format detection / PKCS#7), §2.2 (dss-cades module) — [[sources/SRC-2026-06-27-001]]
- DSS 6.4 API Javadoc — [[sources/dss-timestamp-api-research]]
- ETSI EN 319 102-1 V1.4.1 — [[sources/etsi-en-319-102-1-timestamp]]
- RFC 5544 + RFC 5955 + CNIPA 45/2009 — [[sources/rfc5544-tsd-standard]]
- DSS format detection source code — [[sources/dss-format-detection-research]]
- DSS counter-signature behavior — [[sources/dss-counter-signature-research]]
- Progetto: design spec §6 — [[sources/SRC-2026-06-27-002]]; verification guide — [[sources/SRC-2026-06-27-003]]

## Related
- [[entities/dss]] · [[entities/signeddocumentvalidator]] · [[entities/detachedtimestampvalidator]]
- [[concepts/timestamping]] · [[concepts/baseline-profiles]] · [[concepts/signature-validation]]
- [[entities/dss-validator-adapter]] · [[entities/certificate-verifier]] · [[concepts/reports]]
- [[concepts/rfc5544-tsd]] · [[concepts/dss-timestamp-api]] · [[concepts/dss-format-detection]]
- [[concepts/etsi-en-319-102-1-validation]] · [[concepts/cades-counter-signatures]]
- [[analyses/tsd-dto-mapping]]
- [[concepts/trusted-lists]] · [[concepts/validation-profiles]]