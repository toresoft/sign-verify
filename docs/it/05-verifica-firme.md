# 4. Verifica delle firme

← [4. Trusted Certificates](04-trusted-certificates.md) · [Indice](README.md) · → [6. Estrazione file](06-estrazione-file.md)

- [4.1 Introduzione](#41-introduzione)
- [4.2 Profili di validazione](#42-profili-di-validazione)
  - [Formato della policy XML](#formato-della-policy-xml)
- [4.3 API di verifica sincrona](#43-api-di-verifica-sincrona)
- [4.4 API di verifica asincrona](#44-api-di-verifica-asincrona)

## 4.1 Introduzione

Il servizio verifica firme elettroniche eIDAS nei formati **PAdES** (PDF),
**CAdES** (`.p7m`), **XAdES** (XML), **JAdES** (JSON) e contenitori **ASiC**
(ASiC-S / ASiC-E), usando la libreria **DSS 6.4** e le **EU Trusted Lists** come
ancore di fiducia.

### Pipeline di validazione

```mermaid
flowchart TD
    F[Documento firmato] --> P[Risoluzione profilo\ndefault o profileId]
    P --> O{Override richiesti?}
    O -- sì --> AP[PolicyOverrideApplier\nmodifica i Level della policy]
    O -- no --> DSS
    AP --> DSS[Adapter DSS\nSignedDocumentValidator]
    DSS --> TSL[(EU Trusted Lists)]
    DSS --> R[Risultato:\nindication / subIndication\nsignatureFormat / count]
    R --> REP[Report richiesti\nsimple / detailed / diagnostic / etsi]

    classDef input fill:#dbeeff,stroke:#2f6fbb,color:#0b2e4f
    classDef app fill:#eef1f5,stroke:#5b6b7c,color:#1f2733
    classDef adapter fill:#ede7f6,stroke:#6c4f9c,color:#2c1f47
    classDef external fill:#fff1d6,stroke:#b9842a,color:#4a3203
    class F input
    class P,O,R,REP app
    class AP,DSS adapter
    class TSL external
```

L'esito principale di DSS è espresso da:

- **`indication`** è l'esito complessivo: `TOTAL_PASSED`, `TOTAL_FAILED`,
  `INDETERMINATE`.
- **`subIndication`** è la motivazione di dettaglio quando l'esito non è
  `TOTAL_PASSED` (es. `SIG_CRYPTO_FAILURE`, `NO_CERTIFICATE_CHAIN_FOUND`,
  `OUT_OF_BOUNDS_NO_POE`…).
- **`signatureFormat`** è il formato/livello rilevato (es. `PAdES-BASELINE-B`).
- **`signatureCount`** è il numero di firme trovate.
- **`signatures[]`** elenca il dettaglio per firma: `id`, `indication`, `subIndication`,
  `signatureFormat`, **`signatureLevel`** (qualifica eIDAS DSS: `QESIG`/`QESEAL`,
  `ADESIG_QC`/…, `NA`, varianti `INDETERMINATE_*`; ortogonale a `indication`),
  `signedBy`, `bestSignatureTime`, e `timestamps[]` della firma.
- **`timestamps[]`** elenca le marche del documento: `id`, `indication`, `subIndication`,
  `productionTime`, `qualification` (`QTSA`/`TSA`/`NA`).

### Tipi di report

| Report | Descrizione |
|--------|-------------|
| `simple` | Report sintetico (esito per firma) |
| `detailed` | Report dettagliato dei singoli vincoli di policy |
| `diagnostic` | Dati grezzi (diagnostic data) raccolti da DSS |
| `etsi` | Validation report ETSI (TS 119 102-2) |

Concorrenza: le verifiche sincrone sono limitate da un semaforo
(`app.verify.max-concurrent`, default `8`); oltre il limite si ottiene **503**
(`excessive-load.concurrency`).

## 4.2 Profili di validazione

Un **profilo** incapsula una **policy di validazione DSS** (XML dei vincoli). Il
profilo determina con quale severità vengono valutati i vincoli (revoca,
qualificazione, timestamp, ecc.).

### Preset disponibili

| Preset | File policy | Note |
|--------|-------------|------|
| `BASIC` | `policy/BASIC.xml` | Vincoli minimi |
| `STANDARD` | `policy/STANDARD.xml` | Policy DSS di default (QES/AES su base TSL), **seminato come default** |
| `STRICT` | `policy/STRICT.xml` | Vincoli più severi |
| `CUSTOM` | - | Policy XML fornita dall'utente |

All'avvio, se non esiste alcun profilo, viene seminato il profilo **STANDARD**
(`isDefault = true`).

### Formato della policy XML

La policy è un documento XML nel **namespace DSS**
`http://dss.esig.europa.eu/validation/policy`, con radice
`<ConstraintsParameters>`. È lo stesso formato usato dalla libreria DSS (cfr.
*DSS: Validation policy*); i file `policy/BASIC.xml`, `policy/STANDARD.xml`,
`policy/STRICT.xml` sono esempi completi inclusi nel servizio.

#### Il concetto chiave: l'attributo `Level`

Quasi ogni vincolo porta un attributo `Level` che ne stabilisce la severità.
Determina **come** un controllo fallito influisce sull'esito (`indication` /
`subIndication`):

| `Level` | Significato | Effetto sull'esito |
|---------|-------------|--------------------|
| `FAIL` | Vincolo obbligatorio | Se non soddisfatto → `TOTAL_FAILED` / `INDETERMINATE` |
| `WARN` | Avviso | Non blocca l'esito; segnalato nel report |
| `INFORM` | Informativo | Solo informazione nel report |
| `IGNORE` | Disattivato | Il controllo non viene eseguito |

Rendere un profilo più severo significa alzare il `Level` di alcuni vincoli
(es. `WARN` → `FAIL`); rilassarlo significa abbassarlo (es. `FAIL` → `IGNORE`).
È esattamente ciò che fanno gli [override al volo](#override-al-volo), che
portano selettivamente alcuni vincoli a `IGNORE`.

#### Struttura del documento

I vincoli sono raggruppati in sezioni tematiche, valutate da DSS nelle varie
fasi della validazione:

| Sezione | Vincoli su |
|---------|-----------|
| `<ContainerConstraints>` | Contenitori ASiC-S / ASiC-E (tipi accettati, manifest, file firmati) |
| `<PDFAConstraints>` | Conformità PDF/A (per PAdES) |
| `<SignatureConstraints>` | La firma e la sua catena di certificati (blocco principale) |
| `<CounterSignatureConstraints>` | Eventuali contro-firme |
| `<Timestamp>` | Marche temporali (TSA) |
| `<Revocation>` | Dati di revoca (CRL / OCSP) |
| `<EvidenceRecord>` | Evidence record (RFC 4998 / 6283) |
| `<Cryptographic>` | Algoritmi e dimensioni di chiave ammessi + scadenze |
| `<Model Value="…"/>` | Modello di validazione: `SHELL`, `CHAIN` o `HYBRID` |
| `<eIDAS>` | Vincoli sulle Trusted List (freschezza, firma, versione TL) |

Dentro `<SignatureConstraints>` i controlli sui certificati sono a loro volta
annidati in `<BasicSignatureConstraints>` → `<SigningCertificate>` (il
certificato del firmatario) e `<CACertificate>` (gli emittenti della catena),
più `<SignedAttributes>` per gli attributi firmati (es. `SigningTime`).

#### Forme dei vincoli

- **Vincolo semplice**: ha solo `Level`:
  ```xml
  <SignatureIntact Level="FAIL" />
  ```
- **Vincolo con elenco di valori**: accetta uno o più `<Id>` ammessi:
  ```xml
  <AcceptableContainerTypes Level="FAIL">
      <Id>ASiC-S</Id>
      <Id>ASiC-E</Id>
  </AcceptableContainerTypes>
  ```
- **Vincolo temporale**: usa `Unit` e `Value`:
  ```xml
  <RevocationFreshness Level="IGNORE" Unit="DAYS" Value="0" />
  <TLFreshness Level="WARN" Unit="HOURS" Value="6" />
  ```
- **Sezione `<Cryptographic>`**: elenca algoritmi di cifratura/digest ammessi,
  dimensioni minime di chiave e date di scadenza (`<AlgoExpirationDate>`), per
  rifiutare crittografia obsoleta (es. SHA-1, RSA-1024 oltre una certa data).

#### Esempio minimale commentato

```xml
<ConstraintsParameters Name="esempio minimale"
    xmlns="http://dss.esig.europa.eu/validation/policy">
  <!-- Solo container ASiC-E ammessi -->
  <ContainerConstraints>
    <AcceptableContainerTypes Level="FAIL">
      <Id>ASiC-E</Id>
    </AcceptableContainerTypes>
  </ContainerConstraints>
  <SignatureConstraints>
    <AcceptableFormats Level="FAIL">
      <Id>*</Id> <!-- qualunque formato di firma -->
    </AcceptableFormats>
    <BasicSignatureConstraints>
      <SignatureIntact Level="FAIL" />          <!-- la firma deve essere integra -->
      <ProspectiveCertificateChain Level="FAIL" /> <!-- catena verso una TL -->
      <SigningCertificate>
        <NotExpired Level="WARN" />             <!-- non bloccante se scaduto -->
        <NotRevoked Level="FAIL" />
        <RevocationDataAvailable Level="IGNORE" /> <!-- non richiedere revoca -->
      </SigningCertificate>
    </BasicSignatureConstraints>
  </SignatureConstraints>
  <Cryptographic Level="FAIL">
    <AcceptableDigestAlgo>
      <Algo>SHA256</Algo>
      <Algo>SHA512</Algo>
    </AcceptableDigestAlgo>
  </Cryptographic>
  <Model Value="SHELL" />
</ConstraintsParameters>
```

> Differenza fra i preset: `BASIC`, `STANDARD` e `STRICT` condividono la stessa
> struttura ma si distinguono per i `Level` assegnati ai singoli vincoli:
> `STRICT` promuove a `FAIL` controlli che `BASIC` lascia a `WARN`/`IGNORE`.
> Per i dettagli completi della grammatica fare riferimento alla documentazione
> ufficiale DSS (*Validation policy* / XSD `policy.xsd`).

### Gestione dei profili (API)

| Metodo | Path | Operazione |
|--------|------|-----------|
| `GET` | `/api/v1/profiles?page=&size=` | Elenco (paginato, vedi [Convenzioni](README.md#paginazione)) |
| `POST` | `/api/v1/profiles` | Crea (`name`, `preset`, `policyXml?`) |
| `GET` | `/api/v1/profiles/{id}` | Dettaglio |
| `PUT` | `/api/v1/profiles/{id}` | Aggiorna (`description?`, `policyXml?`) |
| `DELETE` | `/api/v1/profiles/{id}` | Elimina |
| `POST` | `/api/v1/profiles/{id}/default` | Imposta come default |

Creazione di un profilo CUSTOM:

```bash
curl -sS -X POST http://localhost:8080/api/v1/profiles \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"name":"strict-pades","preset":"CUSTOM","policyXml":"<ConstraintsParameters …>…</…>"}'
```

> `policyXml` è obbligatorio quando `preset = CUSTOM`.

### Override al volo

Senza creare un profilo, si può **rilassare** alcuni controlli per la singola
richiesta passando override booleani nei metadati. Impostando una chiave a
`false`, i corrispondenti vincoli della policy vengono portati a `Level=IGNORE`:

| Chiave override | Vincoli interessati (Level → IGNORE) |
|-----------------|--------------------------------------|
| `checkRevocation` | `RevocationDataAvailable`, `RevocationDataFreshness`, `RevocationCertHashMatch` |
| `checkSignatureIntegrity` | `SignatureIntact`, `SignatureValid` |
| `checkCertificateChain` | `ProspectiveCertificateChain`, `TrustedServiceStatus` |
| `checkTimestamp` | `TimestampDelay`, `MessageImprintDataIntact` |
| `checkQualified` | `QualifiedCertificate` |

Gli override si applicano solo per disabilitare un controllo (valore `false`).
La risposta segnala `overridesApplied: true`.

## 4.3 API di verifica sincrona

`POST /api/v1/verifications`: `multipart/form-data`.

| Parte | Tipo | Obbligatoria | Descrizione |
|-------|------|--------------|-------------|
| `file` | binary | sì | Il documento firmato da verificare |
| `metadata` | application/json | no | Oggetto JSON con opzioni di verifica |
| `metadata.profileId` | `string` | no | UUID del profilo di validazione; se assente, si usa il profilo di default |
| `metadata.profileOverrides` | `object` | no | Mappa chiave-valore di override booleani per rilassare controlli specifici (vedi §4.2) |
| `metadata.profileOverrides.checkRevocation` | `boolean` | no | Porta i vincoli di revoca a `IGNORE` |
| `metadata.profileOverrides.checkSignatureIntegrity` | `boolean` | no | Porta i vincoli di integrità della firma a `IGNORE` |
| `metadata.profileOverrides.checkCertificateChain` | `boolean` | no | Porta i vincoli sulla catena di certificati a `IGNORE` |
| `metadata.profileOverrides.checkTimestamp` | `boolean` | no | Porta i vincoli sui timestamp a `IGNORE` |
| `metadata.profileOverrides.checkQualified` | `boolean` | no | Porta il vincolo sul certificato qualificato a `IGNORE` |
| `metadata.reports` | `array[string]` | no | Tipi di report richiesti; default `["simple","etsi"]` |

Se `metadata` è assente, vengono prodotti i report `simple` ed `etsi` con il
profilo di default.

```bash
curl -sS -X POST http://localhost:8080/api/v1/verifications \
  -H "X-API-Key: $KEY" \
  -F 'file=@contratto.pdf' \
  -F 'metadata={"reports":["simple","detailed"],"profileOverrides":{"checkRevocation":false}}'
```

Risposta `200`:

```json
{
  "verifiedAt": "2026-06-08T10:15:30Z",
  "profileUsed": "STANDARD",
  "overridesApplied": true,
  "signatureFormat": "PAdES-BASELINE-B",
  "indication": "TOTAL_PASSED",
  "subIndication": null,
  "signatureCount": 1,
  "signatures": [
    {
      "id": "S-1",
      "indication": "TOTAL_PASSED",
      "subIndication": null,
      "signatureFormat": "PAdES_BASELINE_B",
      "signatureLevel": "QESIG",
      "signedBy": "CN=Mario Rossi, …",
      "bestSignatureTime": "2026-06-08T10:14:00Z",
      "timestamps": []
    }
  ],
  "timestamps": [],
  "reports": {
    "simple":   { /* … */ },
    "detailed": { /* … */ }
  }
}
```

Valori ammessi per `reports`: `simple`, `detailed`, `diagnostic`, `etsi`.
Un valore sconosciuto produce **400** (`unknown report type`). Un JSON di
metadata malformato produce **400** (`invalid metadata json`).

### Timestamp del documento vs timestamp di archivio

La risposta contiene due array di timestamp a livello di firma:

- **`timestamps[]`** contiene i timestamp di firma (livello T/LT): timestamp di
  contenuto incorporati nella firma o che la coprono. Provano che la firma
  esisteva prima del momento di produzione del timestamp, al livello T
  (Timestamp) o LT (Long-Term).
- **`archiveTimestamps[]`** contiene i timestamp di archivio (livello LTA).
  Provengono dagli **evidence record** (RFC 4998 / 6283) e forniscono prova di
  archiviazione a lungo termine (LTA). Vengono popolati solo quando la libreria
  DSS rileva evidence record nella firma.

> **Nota:** `archiveTimestamps` può essere vuoto anche su firme LTA se la EU
> Trusted List (TSL) non è attiva, perché DSS richiede l'accesso alla TSL per
> elaborare completamente gli evidence record.

### Campi della risposta

| Campo | Tipo JSON | Obbligatorio | Descrizione | Valori possibili |
|-------|-----------|--------------|-------------|------------------|
| `verifiedAt` | `string` | sì | Timestamp della verifica (ISO-8601 UTC) | es. `2026-06-08T10:15:30Z` |
| `profileUsed` | `string` | sì | Nome del profilo di validazione applicato | `BASIC`, `STANDARD`, `STRICT`, oppure un nome personalizzato |
| `overridesApplied` | `boolean` | sì | Indica se sono stati applicati override al volo sulla policy | `true`, `false` |
| `signatureFormat` | `string` | sì | Formato/livello della firma rilevato | es. `PAdES-BASELINE-B`, `CAdES-BASELINE-B` |
| `indication` | `string` | sì | Esito complessivo della validazione | `TOTAL_PASSED`, `TOTAL_FAILED`, `INDETERMINATE` |
| `subIndication` | `string` o `null` | sì | Motivazione di dettaglio quando non è `TOTAL_PASSED` | `null`, oppure codici definiti da DSS come `SIG_CRYPTO_FAILURE`, `NO_CERTIFICATE_CHAIN_FOUND`, `OUT_OF_BOUNDS_NO_POE` |
| `signatureCount` | `integer` | sì | Numero di firme trovate nel documento | `0`, `1`, `2`… |
| `signatures` | `array` | sì | Dettagli per firma | Array di oggetti `Signature` |
| `signatures[].id` | `string` | sì | Identificativo della firma | es. `S-1` |
| `signatures[].indication` | `string` | sì | Esito a livello di firma | `TOTAL_PASSED`, `TOTAL_FAILED`, `INDETERMINATE` |
| `signatures[].subIndication` | `string` o `null` | sì | Motivazione di dettaglio a livello di firma | `null`, oppure codici definiti da DSS |
| `signatures[].signatureFormat` | `string` | sì | Formato di firma rilevato per questa firma | es. `PAdES_BASELINE_B` |
| `signatures[].signatureLevel` | `string` | sì | Qualifica eIDAS della firma | `QESIG`, `QESEAL`, `ADESIG_QC`, `NA`, `INDETERMINATE_*` |
| `signatures[].signedBy` | `string` | sì | Distinguished Name del firmatario | es. `CN=Mario Rossi, …` |
| `signatures[].bestSignatureTime` | `string` o `null` | sì | Miglior tempo di firma provato (ISO-8601 UTC) | es. `2026-06-08T10:14:00Z`, oppure `null` |
| `signatures[].claimedSigningTime` | `string` o `null` | no | Tempo di firma dichiarato negli attributi firmati (ISO-8601 UTC) | es. `2026-06-08T10:13:00Z`, oppure `null` |
| `signatures[].timestamps` | `array` | sì | Marche temporali della firma (livello T/LT) | Array di oggetti `Timestamp` |
| `signatures[].timestamps[].id` | `string` | sì | Identificativo della marca temporale | es. `T-1` |
| `signatures[].timestamps[].indication` | `string` | sì | Esito della validazione della marca temporale | `PASSED`, `FAILED`, `INDETERMINATE` |
| `signatures[].timestamps[].subIndication` | `string` o `null` | sì | Motivazione di dettaglio della marca temporale | `null`, oppure codici definiti da DSS |
| `signatures[].timestamps[].productionTime` | `string` o `null` | sì | Tempo di produzione della marca temporale (ISO-8601 UTC) | es. `2026-06-08T10:14:00Z`, oppure `null` |
| `signatures[].timestamps[].qualification` | `string` | sì | Qualifica della TSA | `QTSA`, `TSA`, `NA` |
| `signatures[].archiveTimestamps` | `array` | sì | Marche temporali di archivio (livello LTA/evidence record) | Array di oggetti `ArchiveTimestamp`; vuoto se assenti |
| `signatures[].archiveTimestamps[].id` | `string` | sì | Identificativo della marca di archivio | es. `A-1` |
| `signatures[].archiveTimestamps[].productionTime` | `string` o `null` | sì | Tempo di produzione della marca di archivio (ISO-8601 UTC) | es. `2026-06-08T10:14:00Z`, oppure `null` |
| `signatures[].certificates` | `array` | sì | Certificati coinvolti nella firma | Array di oggetti `Certificate` |
| `signatures[].certificates[].id` | `string` | sì | Identificativo del certificato | es. `C-1` |
| `signatures[].certificates[].qualifiedName` | `string` | sì | Nome qualificato del soggetto del certificato | es. `CN=Mario Rossi, …` |
| `signatures[].certificates[].sunsetDate` | `string` o `null` | sì | Data di sunset del certificato se applicabile (ISO-8601 UTC) | es. `2027-12-31T23:59:59Z`, oppure `null` |
| `timestamps` | `array` | sì | Marche temporali a livello documento | Array di oggetti `Timestamp` (stesso schema di `signatures[].timestamps[]`) |
| `reports` | `object` | sì | Report di validazione richiesti | Oggetto con chiavi `simple`, `detailed`, `diagnostic`, `etsi` |
| `reports.simple` | `object` o `null` | sì* | Report sintetico (esito per firma) | `null` se non richiesto |
| `reports.detailed` | `object` o `null` | sì* | Report dettagliato dei singoli vincoli di policy | `null` se non richiesto |
| `reports.diagnostic` | `object` o `null` | sì* | Dati grezzi (diagnostic data) raccolti da DSS | `null` se non richiesto |
| `reports.etsi` | `object` o `null` | sì* | Validation report ETSI (TS 119 102-2) | `null` se non richiesto |

> \* I sotto-campi di `reports` sono sempre presenti ma possono essere `null` quando il corrispondente tipo di report non è stato richiesto.

### Valori degli enum

**`indication`**

| Valore | Significato |
|--------|-------------|
| `TOTAL_PASSED` | La firma è valida e tutti i vincoli sono stati superati |
| `TOTAL_FAILED` | La firma non è valida: almeno un vincolo FAIL non è stato superato |
| `INDETERMINATE` | L'esito non può essere determinato (es. TSL mancante, informazioni insufficienti) |

**`signatureLevel`** è la qualifica eIDAS DSS (ETSI TS 119 102-1) ed è ortogonale a `indication`.

| Valore | Significato |
|--------|-------------|
| `QESIG` | Firma Elettronica Qualificata |
| `QESEAL` | Sigillo Elettronico Qualificato |
| `ADESIG_QC` | Firma Elettronica Avanzata con certificato qualificato |
| `ADESEAL_QC` | Sigillo Elettronico Avanzato con certificato qualificato |
| `NA` | Non valutato / non applicabile |
| `INDETERMINATE_QESIG` | Firma elettronica qualificata indeterminata |
| `INDETERMINATE_QESEAL` | Sigillo elettronico qualificato indeterminato |
| `INDETERMINATE_ADESIG_QC` | AdESig con certificato qualificato indeterminata |
| `INDETERMINATE_ADESEAL_QC` | AdESeal con certificato qualificato indeterminata |

### Risposte di errore

Vedi il [catalogo errori](07-log-audit.md#62-gestione-degli-errori-problemjson)
completo per ogni codice. Quelli più probabili su questo endpoint:

**400: metadata malformato**

```json
{"type":"urn:signverify:error:validation.invalid-input","title":"Bad Request","status":400,"detail":"invalid metadata json","instance":"/api/v1/verifications"}
```

**413: file troppo grande**

```json
{"type":"urn:signverify:error:payload.too-large","title":"Payload Too Large","status":413,"detail":"max upload size exceeded","instance":"/api/v1/verifications"}
```

**503: limite di concorrenza raggiunto**

```json
{"type":"urn:signverify:error:excessive-load.concurrency","title":"Service Unavailable","status":503,"detail":"verify concurrency limit reached","instance":"/api/v1/verifications"}
```

**200 con `INDETERMINATE`**

```json
{
  "verifiedAt": "2026-06-08T10:15:30Z",
  "profileUsed": "STANDARD",
  "overridesApplied": false,
  "signatureFormat": "PAdES-BASELINE-B",
  "indication": "INDETERMINATE",
  "subIndication": "NO_CERTIFICATE_CHAIN_FOUND",
  "signatureCount": 1,
  "signatures": [
    {
      "id": "S-1",
      "indication": "INDETERMINATE",
      "subIndication": "NO_CERTIFICATE_CHAIN_FOUND",
      "signatureFormat": "PAdES_BASELINE_B",
      "signatureLevel": "INDETERMINATE_QESIG",
      "signedBy": "CN=Mario Rossi, …",
      "bestSignatureTime": "2026-06-08T10:14:00Z",
      "claimedSigningTime": null,
      "timestamps": [],
      "archiveTimestamps": [],
      "certificates": []
    }
  ],
  "timestamps": [],
  "reports": { "simple": { }, "etsi": { } }
}
```

```mermaid
sequenceDiagram
    autonumber
    box rgb(219,238,255) Client
    participant C as Client
    end
    box rgb(238,241,245) sign-verify
    participant V as VerificationController
    participant S as VerificationService
    participant D as Adapter DSS
    end
    C->>V: POST /verifications (file + metadata)
    V->>S: verifySync(file, profileId, overrides, reports)
    S->>S: tryAcquire semaforo (max-concurrent)
    alt limite raggiunto
        S-->>C: 503 excessive-load.concurrency
    else acquisito
        S->>D: validate(file, policyXml, reports)
        D-->>S: ValidationResult
        S-->>V: profilo + risultato
        V-->>C: 200 + report
    end
```

## 4.4 API di verifica asincrona

Per documenti grandi o per consegna tramite **webhook**, si usa il flusso
asincrono basato su job persistiti.

### Sottomissione

`POST /api/v1/verifications/async`: `multipart/form-data` (`file` + `metadata`).

Campi di `metadata` (JSON): `profileId?`, `profileOverrides?`, `reports[]?`
(default `simple,diagnostic`), `callbackUrl?`, `callbackSecret?`,
`callbackAlgorithm?`.

```bash
curl -sS -X POST http://localhost:8080/api/v1/verifications/async \
  -H "X-API-Key: $KEY" \
  -F 'file=@grande.pdf' \
  -F 'metadata={"reports":["simple","etsi"],"callbackUrl":"https://app.example.org/hook","callbackSecret":"s3cr3t","callbackAlgorithm":"HmacSHA256"}'
```

Risposta `202`:

```json
{ "jobId": "…", "status": "PENDING" }
```

con header `Location: /api/v1/verifications/jobs/<jobId>`.

**Backpressure**: se i job attivi superano il limite per principal
(`max-pending-per-principal`, default 50) o globale (`max-pending-global`,
default 500), si ottiene **429** (`excessive-load.async-backpressure`).

### Ciclo di vita del job

```mermaid
stateDiagram-v2
    [*] --> PENDING: submit
    PENDING --> RUNNING: worker preso in carico
    RUNNING --> COMPLETED: validazione OK (no callback)
    RUNNING --> FAILED: errore validazione
    COMPLETED --> DELIVERED: callback consegnato
    COMPLETED --> DELIVERY_FAILED: callback esaurito
    COMPLETED --> DELETED: retention/cleanup
    DELIVERED --> DELETED: retention/cleanup
    FAILED --> DELETED: retention/cleanup
    DELETED --> [*]

    classDef inProgress fill:#dbeeff,stroke:#2f6fbb,color:#0b2e4f
    classDef success fill:#e1f5e9,stroke:#2f8a4e,color:#0d3a1d
    classDef failure fill:#fde2e1,stroke:#b8413a,color:#4a0f0c
    classDef terminal fill:#eef1f5,stroke:#5b6b7c,color:#1f2733
    class PENDING,RUNNING inProgress
    class COMPLETED,DELIVERED success
    class FAILED,DELIVERY_FAILED failure
    class DELETED terminal
```

Stati (`JobStatus`): `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `DELIVERED`,
`DELIVERY_FAILED`, `DELETED`.

### Valori di JobStatus

| Valore | Significato |
|--------|-------------|
| `PENDING` | Job sottomesso, in attesa di essere prelevato |
| `RUNNING` | Job in elaborazione |
| `COMPLETED` | Validazione completata con successo |
| `FAILED` | Validazione fallita con errore |
| `DELIVERED` | Risultato consegnato al callback con successo |
| `DELIVERY_FAILED` | Consegna al callback esaurita senza successo |
| `DELETED` | Job scaduto e rimosso dalla retention |

Il **ValidationWorker** fa polling (`async.worker.poll-interval`, default `5s`),
preleva i job pending e li elabora; se il circuit breaker `dssValidator` è
**OPEN**, salta il ciclo. Il segreto del callback è cifrato a riposo
(AES-256-GCM) con la master-key.

### Recupero del risultato

`GET /api/v1/verifications/jobs/{jobId}`.

- Visibile al **proprietario** del job o a un principal **PRIVILEGED**;
  altrimenti **404** (per non rivelare l'esistenza del job).
- Se lo stato è `DELETED`, il risultato non è più disponibile: **410 Gone**.

```json
{
  "jobId": "…",
  "status": "DELIVERED",
  "createdAt": "…", "startedAt": "…", "completedAt": "…", "deliveredAt": "…",
  "expiresAt": "…",
  "callbackAttempts": 1,
  "result": { /* report JSON */ }
}
```

### Consegna via callback (webhook)

```mermaid
sequenceDiagram
    autonumber
    box rgb(238,241,245) sign-verify
    participant W as ValidationWorker
    participant CW as CallbackWorker
    end
    box rgb(255,241,214) Esterno
    participant E as Endpoint del client
    end
    W->>W: job COMPLETED, callbackUrl presente
    CW->>CW: SSRF guard (no HTTP, no reti private)
    CW->>E: POST body firmato HMAC<br/>X-Signature, X-Timestamp, X-Nonce, X-Delivery-Id
    alt 2xx
        E-->>CW: 200 → DELIVERED
    else stato ritentabile
        E-->>CW: 5xx/429 → retry con backoff
        CW->>E: nuovo tentativo (max-attempts)
        CW->>CW: esauriti → DELIVERY_FAILED
    end
```

Il dispatcher firma il corpo con HMAC (`HmacSHA256` default, o `HmacSHA512`) e
include header di firma e anti-replay:

| Header | Significato |
|--------|-------------|
| `X-Signature` | HMAC del corpo (+ timestamp/nonce/deliveryId) |
| `X-Signature-Algorithm` | algoritmo usato |
| `X-Timestamp`, `X-Nonce` | anti-replay |
| `X-Job-Id`, `X-Delivery-Id`, `X-Delivery-Attempt` | correlazione |

**Corpo**: `Content-Type: application/json`. Su `COMPLETED`, `result` è lo
stesso oggetto restituito da `GET /api/v1/verifications/jobs/{jobId}`:

```json
{
  "jobId": "5e9b1f2a-...",
  "status": "COMPLETED",
  "result": { "indication": "TOTAL_PASSED", "...": "..." }
}
```

Su `FAILED`, `result` è assente e `error` riporta il messaggio di errore:

```json
{
  "jobId": "5e9b1f2a-...",
  "status": "FAILED",
  "error": "signature.parse-error: unrecognised document format"
}
```

> **Verificare `X-Signature`**: non è un semplice HMAC del corpo. Calcola
> `sha256(corpo)` come stringa hex, costruisci la stringa canonica
> `<X-Timestamp>\n<X-Nonce>\n<X-Delivery-Id>\n<bodyHashHex>`, poi calcola
> l'HMAC di questa stringa con `callbackSecret` usando l'algoritmo indicato in
> `X-Signature-Algorithm`. Il valore dell'header è `sha256=<hex>` oppure
> `sha512=<hex>`. Ricalcola e confronta prima di fidarti del corpo, e rifiuta
> richieste con un `X-Timestamp` non recente per prevenire replay.

**Guardia anti-SSRF**: di default sono ammessi solo URL **HTTPS**
(`allow-http=false`) e sono bloccati gli host che risolvono a indirizzi
**privati/non instradabili** (loopback, link-local incl. `169.254.169.254`,
site-local, ULA IPv6, multicast). Un host non risolvibile è trattato come
privato (fail-closed). Politica di retry: `max-attempts` (default 3), backoff
`60s,300s,1800s`, stati di successo `200,201,202,204`, stati ritentabili
`408,425,429,500,502,503,504`.
