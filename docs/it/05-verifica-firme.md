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
```

L'esito principale di DSS è espresso da:

- **`indication`** — esito complessivo: `TOTAL_PASSED`, `TOTAL_FAILED`,
  `INDETERMINATE`.
- **`subIndication`** — motivazione di dettaglio quando non è `TOTAL_PASSED`
  (es. `SIG_CRYPTO_FAILURE`, `NO_CERTIFICATE_CHAIN_FOUND`, `OUT_OF_BOUNDS_NO_POE`…).
- **`signatureFormat`** — formato/livello rilevato (es. `PAdES-BASELINE-B`).
- **`signatureCount`** — numero di firme trovate.
- **`signatures[]`** — dettaglio per firma: `id`, `indication`, `subIndication`,
  `signatureFormat`, **`signatureLevel`** (qualifica eIDAS DSS: `QESIG`/`QESEAL`,
  `ADESIG_QC`/…, `NA`, varianti `INDETERMINATE_*`; ortogonale a `indication`),
  `signedBy`, `bestSignatureTime`, e `timestamps[]` della firma.
- **`timestamps[]`** — marche del documento: `id`, `indication`, `subIndication`,
  `productionTime`, `qualification` (`QTSA`/`TSA`/`NA`).

### Tipi di report

| Report | Descrizione |
|--------|-------------|
| `simple` | Report sintetico (esito per firma) |
| `detailed` | Report dettagliato dei singoli vincoli di policy |
| `diagnostic` | Dati grezzi (diagnostic data) raccolti da DSS |
| `etsi` | Validation report ETSI (TS 119 102-2) |

Concorrenza: le verifiche sincrone sono limitate da un semaforo
(`app.verify.max-concurrent`, default `8`); oltre il limite si ottiene **429**
(`excessive-load.concurrency`).

## 4.2 Profili di validazione

Un **profilo** incapsula una **policy di validazione DSS** (XML dei vincoli). Il
profilo determina con quale severità vengono valutati i vincoli (revoca,
qualificazione, timestamp, ecc.).

### Preset disponibili

| Preset | File policy | Note |
|--------|-------------|------|
| `BASIC` | `policy/BASIC.xml` | Vincoli minimi |
| `STANDARD` | `policy/STANDARD.xml` | Policy DSS di default (QES/AES su base TSL) — **seminato come default** |
| `STRICT` | `policy/STRICT.xml` | Vincoli più severi |
| `CUSTOM` | — | Policy XML fornita dall'utente |

All'avvio, se non esiste alcun profilo, viene seminato il profilo **STANDARD**
(`isDefault = true`).

### Formato della policy XML

La policy è un documento XML nel **namespace DSS**
`http://dss.esig.europa.eu/validation/policy`, con radice
`<ConstraintsParameters>`. È lo stesso formato usato dalla libreria DSS (cfr.
*DSS — Validation policy*); i file `policy/BASIC.xml`, `policy/STANDARD.xml`,
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

- **Vincolo semplice** — solo `Level`:
  ```xml
  <SignatureIntact Level="FAIL" />
  ```
- **Vincolo con elenco di valori** — uno o più `<Id>` ammessi:
  ```xml
  <AcceptableContainerTypes Level="FAIL">
      <Id>ASiC-S</Id>
      <Id>ASiC-E</Id>
  </AcceptableContainerTypes>
  ```
- **Vincolo temporale** — con `Unit` e `Value`:
  ```xml
  <RevocationFreshness Level="IGNORE" Unit="DAYS" Value="0" />
  <TLFreshness Level="WARN" Unit="HOURS" Value="6" />
  ```
- **Sezione `<Cryptographic>`** — algoritmi di cifratura/digest ammessi,
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
> struttura ma si distinguono per i `Level` assegnati ai singoli vincoli —
> `STRICT` promuove a `FAIL` controlli che `BASIC` lascia a `WARN`/`IGNORE`.
> Per i dettagli completi della grammatica fare riferimento alla documentazione
> ufficiale DSS (*Validation policy* / XSD `policy.xsd`).

### Gestione dei profili (API)

| Metodo | Path | Operazione |
|--------|------|-----------|
| `GET` | `/api/v1/profiles?page=&size=` | Elenco |
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

`POST /api/v1/verifications` — `multipart/form-data`.

| Parte | Obbligatoria | Descrizione |
|-------|--------------|-------------|
| `file` | sì | Il documento firmato (binario) |
| `metadata` | no | JSON: `profileId?`, `profileOverrides?`, `reports[]?` |

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

```mermaid
sequenceDiagram
    participant C as Client
    participant V as VerificationController
    participant S as VerificationService
    participant D as Adapter DSS
    C->>V: POST /verifications (file + metadata)
    V->>S: verifySync(file, profileId, overrides, reports)
    S->>S: tryAcquire semaforo (max-concurrent)
    alt limite raggiunto
        S-->>C: 429 excessive-load.concurrency
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

`POST /api/v1/verifications/async` — `multipart/form-data` (`file` + `metadata`).

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
```

Stati (`JobStatus`): `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `DELIVERED`,
`DELIVERY_FAILED`, `DELETED`.

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
    participant W as ValidationWorker
    participant CW as CallbackWorker
    participant E as Endpoint del client
    W->>W: job COMPLETED, callbackUrl presente
    CW->>CW: SSRF guard (no HTTP, no reti private)
    CW->>E: POST body firmato HMAC\nX-Signature, X-Timestamp, X-Nonce, X-Delivery-Id
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

**Guardia anti-SSRF**: di default sono ammessi solo URL **HTTPS**
(`allow-http=false`) e sono bloccati gli host che risolvono a indirizzi
**privati/non instradabili** (loopback, link-local incl. `169.254.169.254`,
site-local, ULA IPv6, multicast). Un host non risolvibile è trattato come
privato (fail-closed). Politica di retry: `max-attempts` (default 3), backoff
`60s,300s,1800s`, stati di successo `200,201,202,204`, stati ritentabili
`408,425,429,500,502,503,504`.
