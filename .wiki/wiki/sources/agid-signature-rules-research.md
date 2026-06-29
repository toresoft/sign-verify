---
type: source
title: "AgID — regole su firma qualificata/digitale e algoritmi crittografici"
slug: agid-signature-rules-research
status: ingested
created: 2026-06-29
updated: 2026-06-29
category: regulatory
confidence: high
verified: 2026-06-29
volatility: warm
credibility: high
sources:
  - sources/SRC-2026-06-29-001
  - sources/SRC-2026-06-29-002
  - sources/SRC-2026-06-29-003
  - sources/SRC-2026-06-29-004
urls:
  - https://www.agid.gov.it/sites/default/files/repository_files/linee_guida_per_la_sottoscrizione_elettronica_di_documenti_ai_sensi_dellart.20_del_cad.pdf
  - https://docs.italia.it/AgID/documenti-in-consultazione/lg-spid-firma-docs/it/1.1/main/06_crittografia.html
  - https://www.agid.gov.it/sites/default/files/repository_files/circolari/deliberazione_cnipa_45_9_novembre_2009_gu_modificata_dalla_dt_69_2010_1.pdf
  - https://www.agid.gov.it/en/platforms/qualified-electronic-signature
  - https://eidas.agid.gov.it/TL/TSL-IT.xml
  - https://ec.europa.eu/tools/lotl/eu-lotl.xml
---

# AgID — regole firma qualificata/digitale e crittografia (ricerca web 2026-06-29)

Fonti raccolte per fondare i preset di validazione [[concepts/validation-profiles|AGID / AGID_TS]].
Vedi [[concepts/italian-digital-signature-law]] per il quadro normativo completo.

## Fonti

### 1. AgID — Linee Guida sottoscrizione elettronica art. 20 CAD (Det. 157/2020)
`linee_guida_..._art.20_del_cad.pdf` — ingest completo: [[sources/SRC-2026-06-29-001]]

- **Determinazione AgID n. 157/2020** del 23 marzo 2020; pubblicata in **GU n. 90 del 04/04/2020**, in vigore dal **05/04/2020**.
- Definisce le modalità tecnico-operative per la sottoscrizione elettronica ex art. 20 CAD, inclusa la firma con **sigillo qualificato del gestore SPID**.
- QES = risultato di una procedura (validazione) che garantisce autenticità, integrità e non ripudio.

### 2. AgID — Regole Tecniche, cap. crittografia (docs.italia.it)
`lg-spid-firma-docs/.../06_crittografia.html` — ingest completo: [[sources/SRC-2026-06-29-003]]

Algoritmi ammessi (valori esatti):
- **Hash**: SHA-256 obbligatorio per il calcolo delle impronte.
- **RSA**: chiavi asimmetriche ≥ **2048 bit** (hash SHA-256).
- **ECDSA**: curva **P-256** + SHA-256 (JWA `ES256`).
- **TLS** ≥ 1.2 per i protocolli di comunicazione.
- Gli algoritmi possono essere sostituiti/integrati con **Avvisi** sul sito AgID.
- Non specifica nel capitolo: RSA-PSS, DSA, date di dismissione (delegate a ETSI TS 119 312).

### 3. CNIPA — Deliberazione 45/2009 (mod. DT 69/2010)
`deliberazione_cnipa_45_2009_...pdf` — ingest completo: [[sources/SRC-2026-06-29-002]]

- Regole storiche su algoritmi e dimensioni chiavi: RSA ≥ 1024 (firma) / ≥ 2048 (certificazione); `sha256-with-rsa` per CAdES (ETSI TS 101 733); `ecdsa-with-Sha256` per curve ellittiche; `RSA-SHA256` per XAdES.
- Origine storica dell'adozione del formato **TSD** (RFC 5544) come default per la marca temporale nelle TSA italiane accreditate. Vedi [[concepts/rfc5544-tsd]].

### 4. AgID — pagina piattaforma "Qualified Electronic Signature"
`agid.gov.it/en/platforms/qualified-electronic-signature` — ingest completo: [[sources/SRC-2026-06-29-004]]

- Panoramica istituzionale su FEQ, certificati qualificati, sigilli e validazione temporale qualificata.

### 5. Trusted List operative
- **TL italiana (AgID)**: `https://eidas.agid.gov.it/TL/TSL-IT.xml`
- **EU LOTL**: `https://ec.europa.eu/tools/lotl/eu-lotl.xml` (aggrega le TL nazionali; vedi [[concepts/trusted-lists]]).

## Sintesi per i preset AGID

| Requisito | Fonte | Mappato a |
|---|---|---|
| Cert qualificato + QSCD | eIDAS 3(12), CAD 24, AgID Det.157/2020 | `QcCompliance`/`QcSSCD` = FAIL |
| nonRepudiation | DPCM 28, ETSI EN 319 412-2 | `KeyUsage` = FAIL |
| Servizio CA/QC granted | eIDAS 22, AgID TL | `TrustServiceType/Status` = FAIL |
| SHA-256+/RSA-2048/ECDSA-P256 | AgID cap. crittografia, ETSI TS 119 312 | blocco `<Cryptographic>` |
| Marca temporale (opponibilità) | DPCM 62, CNIPA 45/2009 | `TLevelTimeStamp` = FAIL (solo AGID_TS) |

> **Nota credibilità**: fonti primarie AgID/CNIPA (alta). Il capitolo crittografia su docs.italia.it appartiene alle Regole Tecniche per la sottoscrizione SPID (perimetro art. 20 CAD); per la suite crittografica generale di validazione il riferimento operativo resta **ETSI TS 119 312**, già codificato nel blocco `<Cryptographic>` dei preset.

## Related
- [[concepts/italian-digital-signature-law]] · [[concepts/validation-profiles]] · [[concepts/dss-policy-xml]]
- [[concepts/trusted-lists]] · [[concepts/rfc5544-tsd]] · [[entities/eidas-regulation]]
