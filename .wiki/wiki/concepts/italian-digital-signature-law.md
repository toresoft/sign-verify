---
type: concept
domain: regulatory
created: 2026-06-29
updated: 2026-06-29
confidence: high
verified: 2026-06-29
volatility: cold
sources:
  - notes/2026-06-28-quadro-normativo-firma-digitale-eidas
  - sources/SRC-2026-06-29-001
  - sources/SRC-2026-06-29-002
  - sources/SRC-2026-06-29-003
---

# Quadro normativo italiano per la firma digitale

Il diritto italiano recepisce eIDAS ([[entities/eidas-regulation]]) e aggiunge strati normativi nazionali che ne determinano l'applicazione operativa.

## Normativa primaria italiana

### D.Lgs. 7 marzo 2005, n. 82 — CAD (Codice dell'Amministrazione Digitale)

| Articolo | Contenuto rilevante |
|---|---|
| Art. 1 lett. s) | Definizione di firma digitale |
| Art. 1 lett. n-ter) | Dispositivo sicuro per la creazione della firma (SSCD) |
| Art. 20 | Valore probatorio del documento informatico |
| Art. 21 | Efficacia del documento con firma elettronica |
| Art. 24 | Firma digitale — requisiti e validità |
| Art. 24 c. 4-bis | Firma su certificato revocato/scaduto ≡ mancata sottoscrizione |

### DPCM 22 febbraio 2013 — Regole tecniche

(GU n. 117 del 21/05/2013)

| Articolo | Contenuto rilevante |
|---|---|
| Art. 14 | CA qualificate e accreditamento |
| Artt. 21-23 | Revoca/sospensione certificati, gestione CRL |
| Art. 28 | `KeyUsage nonRepudiation` obbligatorio per certificati di sottoscrizione |
| Art. 35 | Dispositivo sicuro per la creazione della firma (SSCD) |
| Artt. 47-50 | Marca temporale: contenuto, chiavi TSA, requisiti |
| Art. 56 | Requisiti della firma elettronica avanzata |
| Art. 61 | Ambito di utilizzo della FEA |
| Art. 62 c. 1 | Validità firma qualificata/digitale anche dopo scadenza/revoca del certificato, con riferimento temporale opponibile a terzi |

**Art. 62 c. 1** è la norma che motiva la verifica LT/LTA: il timestamp opponibile consente di provare che la firma era valida al momento dell'apposizione, anche se il certificato è poi scaduto.

### Decreto CNIPA 45/2009 e marca temporale TSD

**CNIPA Deliberazione 45/2009** (G.U. 3 dicembre 2009) ha mandato il formato RFC 5544 TSD ([[concepts/rfc5544-tsd]]) come default per le TSA italiane accreditate. Causa storica dell'adozione massiva del `.tsd` in PA.

**DPCM 13 novembre 2014** — "Regole tecniche su formazione, trasmissione e validazione temporale": riconosce implicitamente i formati delle CA accreditate, incluso TSD.

## Standard tecnici ETSI rilevanti

| Standard | Oggetto |
|---|---|
| ETSI EN 319 102-1 | Validazione AdES, livelli B/T/LT/LTA, TOTAL-PASSED/INDETERMINATE/TOTAL-FAILED |
| ETSI EN 319 122-1 | CAdES (CMS) |
| ETSI EN 319 132-1 | XAdES (XML) |
| ETSI EN 319 142-1 | PAdES (PDF) |
| ETSI EN 319 162-1 | ASiC-S / ASiC-E |
| ETSI EN 319 412-2 | Profilo certificato qualificato persona fisica (nonRepudiation, QCStatements) |
| ETSI EN 319 412-3 | Profilo certificato TSA (id-kp-timeStamping) |
| ETSI EN 319 412-5 | QCStatements: QcCompliance, QcSSCD, QcLegislationCountryCodes |
| ETSI EN 319 421/422 | Requisiti TSA |
| ETSI TS 119 312 | Cryptographic suites |
| ETSI TS 119 322 v1.2.1 | Cryptographic Suite XML/JSON (alternativa al blocco `<Cryptographic>` in policy DSS) |
| ETSI TS 119 612 v2.3.1/v2.4.1 | Trusted Lists — formato XML, ServiceTypeIdentifier, ServiceStatus, LOTL |

## Fonti istituzionali operative

| Fonte | URL / Riferimento |
|---|---|
| AgID Trusted List italiana | `https://eidas.agid.gov.it/TL/TSL-IT.xml` |
| AgID certificati TL | 5° e 6° certificato, GU n. 291 del 16/12/2025 |
| AgID "Tipologie firme e sigilli" | Documento dicembre 2019 |
| EU LOTL | `https://ec.europa.eu/tools/lotl/eu-lotl.xml` |
| DSS constraint.xml | `dss-cookbook` su GitHub esig/dss |
| Policy XSD | `dss-policy-jaxb/.../xsd/policy.xsd` |
| Nota TLv6 | Decisione CE 2025/2164, applicabile dal 29 aprile 2026 |

## NIST SP 800-57 — Key Management

Scadenze operative degli algoritmi rilevanti:
- RSA 2048 → 2030
- SHA-256 → 2030

Usate come riferimento nel blocco `<AlgoExpirationDate>` nelle policy DSS custom.

## Impatto su sign-verify-2

- L'art. 62 c. 1 DPCM 2013 motiva la verifica `LT`/`LTA`: bisogna poter dimostrare che la firma era valida al momento dell'apposizione.
- Il `KeyUsage nonRepudiation` (art. 28 DPCM) è parte del profilo certificato qualificato che DSS verifica tramite [[entities/certificateverifier]].
- Le TSA italiane usano quasi sempre TSD RFC 5544 (non CAdES-T) per la marca temporale solitaria → necessità del `TsdAwareValidatorAdapter`.
- La TL italiana (AgID) è inclusa nella LOTL → verificata da [[entities/dsstsladapter]].

### Regole tecniche AgID — crittografia

Due fonti, **perimetri distinti** (importante):

**a) Det. 157/2020 — LG sottoscrizione art. 20 CAD ([[sources/SRC-2026-06-29-001]], cap. 6).**
Perimetro: **firma con SPID** (sigillo qualificato del gestore), *non* la verifica QES generica. Vedi [[concepts/firma-con-spid]]. Algoritmi imposti:
- **Hash**: SHA-256 (impronte).
- **ECDSA** curva **P-256** + SHA-256 (`ES256`) per la creazione dei sigilli.
- **RSA** ≥ **2048 bit** + SHA-256 fuori dal contesto JWT.
- **TLS** ≥ 1.2; algoritmi modificabili via *Avvisi* AgID.

**b) CNIPA Deliberazione 45/2009 ([[sources/SRC-2026-06-29-002]]).**
Regole storiche per firma digitale/marca temporale:
- **RSA** ≥ **1024 bit** (firma) / ≥ **2048 bit** (chiavi di certificazione).
- **ECDSA** ammesso (valore di firma digitale).
- **SHA-256** per certificazione, sottoscrizione e marcatura; SHA-1 ammesso solo in *verifica* di marche temporali storiche.
- Formati: **CAdES** (ETSI TS 101 733, `sha256WithRSAEncryption` OID 1.2.840.113549.1.1.11; `ecdsa-with-Sha256` OID 1.2.840.10045.4.3.2), **PAdES** (ETSI TS 102 778, Message Digest SHA-256), **XAdES** (`RSA-SHA256`).

> Per la suite crittografica **di validazione** il riferimento operativo è **ETSI TS 119 312**, già codificato nel blocco `<Cryptographic>` (con `AlgoExpirationDate` datato) del preset `STANDARD` e mantenuto nei preset AGID. Le regole AgID/CNIPA sopra fissano i *minimi* (RSA≥2048, SHA-256, ECDSA P-256) coerenti con quel blocco. Catalogo fonti: [[sources/agid-signature-rules-research]].

### Preset di validazione AGID / AGID_TS (QES-strict)

Codificati in due preset DSS ([[concepts/validation-profiles]]), seedati come profili `agid` e `agid-ts`. Vincoli sulla firma principale, rispetto a `STANDARD`:

| Vincolo | Livello | Base normativa |
|---|---|---|
| `QcCompliance` | `FAIL` | Certificato qualificato — eIDAS art. 28, CAD art. 24 |
| `QcSSCD` | `FAIL` | Chiavi su QSCD — definizione QES eIDAS art. 3(12), CAD art. 24, DPCM art. 35 |
| `KeyUsage nonRepudiation` | `FAIL` | DPCM art. 28, ETSI EN 319 412-2 |
| `TrustServiceTypeIdentifier` = `CA/QC` | `FAIL` | Servizio qualificato in TL — eIDAS art. 22, ETSI TS 119 612 |
| `TrustServiceStatus` (granted + storici) | `FAIL` | Status qualificato operativo/storico |
| `QcLegislationCountryCodes` = `IT` | `WARN` | Accetta altri UE (eIDAS art. 25), segnala se non IT |
| `TLevelTimeStamp` (solo `AGID_TS`) | `FAIL` | Marca temporale T-level valida — opponibilità ex DPCM art. 62 c. 1 |

> **Nota su QcSSCD**: impostato a `FAIL` perché QES/firma digitale richiede per legge il QSCD. DSS determina il QSCD combinando i QCStatement del certificato **e** i qualifier della TL, quindi il vincolo fallisce solo quando la firma non è realmente QES. Se un integratore incontra QES valide per cui DSS non conferma il QSCD, abbassare a `WARN`.

I due XML derivano da `STANDARD.xml`, validati contro `dss-policy-jaxb` `policy.xsd`. Default di sistema invariato (`STANDARD`). Il TSA resta verificato con `ExtendedKeyUsage=timeStamping` (ETSI EN 319 412-3), non `nonRepudiation`.

## See Also

- [[entities/eidas-regulation]] — normativa UE primaria (Reg. 910/2014 e 2024/1183)
- [[concepts/rfc5544-tsd]] — formato TSD, contesto normativo CNIPA 45/2009 + DPCM 2013
- [[concepts/trusted-lists]] — LOTL, TSL, TLv6
- [[concepts/ades-signature-formats]] — PAdES/CAdES/XAdES/JAdES/ASiC
- [[concepts/timestamping]] — marca temporale RFC 3161
- [[concepts/baseline-profiles]] — profili B/T/LT/LTA e rilevanza legale LTA
- [[concepts/validation-profiles]] — preset AGID/AGID_TS · [[concepts/signature-qualification]] · [[concepts/dss-policy-xml]]
- [[concepts/firma-con-spid]] — sottoscrizione SPID ex art. 20 CAD · [[sources/agid-signature-rules-research]]
