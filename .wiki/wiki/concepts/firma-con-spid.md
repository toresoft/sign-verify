---
type: concept
domain: regulatory
created: 2026-06-29
updated: 2026-06-29
confidence: high
verified: 2026-06-29
volatility: warm
sources:
  - sources/SRC-2026-06-29-001
  - sources/SRC-2026-06-29-004
---

# Firma con SPID (sottoscrizione ex art. 20 c. 1-bis CAD)

Modalità di sottoscrizione elettronica introdotta dalle **Linee Guida AgID Det. 157/2020** ([[sources/SRC-2026-06-29-001]]), in vigore dal 5 aprile 2020. Soddisfa il requisito della forma scritta ex **art. 20 CAD** usando l'identità **SPID** (livello 2+) invece di una firma qualificata del firmatario.

## Come funziona

1. Il **Service Provider (SP)** predispone il documento (PDF) alla firma e calcola l'impronta SHA-256.
2. Il firmatario esprime il consenso autenticandosi presso il proprio **Identity Provider (IDP)** SPID.
3. L'IDP (e/o l'SP) appone un **sigillo elettronico qualificato (QSeal)** del gestore sul documento, vincolandolo all'identità del firmatario.

Il risultato è un **PDF sigillato con un QSeal qualificato del gestore SPID** — non una firma qualificata del firmatario.

## Implicazioni per la verifica

- All'atto della validazione ([[entities/sign-verify-2]]), un documento "firmato con SPID" si presenta come **PAdES con sigillo qualificato**: `signatures[].signatureLevel` sarà tipicamente `QESEAL` (sigillo, persona giuridica = gestore), **non** `QESIG`. Vedi [[concepts/signature-qualification]] per la distinzione firma vs sigillo.
- I preset [[concepts/validation-profiles|AGID / AGID_TS]] (QES-strict su `nonRepudiation` + `QcCompliance`) sono tarati sulla **firma qualificata del firmatario**: una firma-con-SPID, essendo un *sigillo*, può non soddisfare il vincolo `KeyUsage=nonRepudiation`. Se il servizio deve accettare anche le firme-con-SPID, valutare un profilo dedicato che ammetta i QSeal (`KeyUsage` diverso, qualifica `QESEAL`).
- L'efficacia giuridica deriva dall'art. 20 CAD + Det. 157/2020, distinta da quella della firma digitale/QES ex art. 24 CAD.

## Crittografia (Det. 157/2020 cap. 6)

SHA-256; ECDSA P-256 (`ES256`) o RSA-2048 + SHA-256 per il QSeal; TLS ≥ 1.2. Dettagli in [[concepts/italian-digital-signature-law]].

## See Also
- [[concepts/italian-digital-signature-law]] — quadro normativo, CAD art. 20 vs 24
- [[concepts/signature-qualification]] — `QESEAL` vs `QESIG` (sigillo vs firma)
- [[concepts/validation-profiles]] — preset AGID (firma qualificata) · [[entities/eidas-regulation]]
- [[sources/agid-signature-rules-research]] — catalogo fonti AgID
