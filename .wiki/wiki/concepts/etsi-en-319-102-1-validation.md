---
type: concept
domain: standards
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/etsi-en-319-102-1-timestamp
---

# ETSI EN 319 102-1 — Algoritmo di validazione AdES con timestamp

Standard normativo: **ETSI EN 319 102-1 V1.4.1** (giugno 2024). Implementazione di riferimento: DSS (eu.europa.eu).

## Sezioni per livello CAdES

| Sezione | Livello | Contenuto |
|---|---|---|
| §5.3 | CAdES-B | Validazione firma base (BBB) |
| §5.4 | TUTTI | Validazione token timestamp (BBB) |
| §5.5 | CAdES-T / CAdES-LT | Firma con tempo + dati long-term |
| §5.6 | CAdES-LTA | Firma con dati archivio |

## §5.4 — Time-stamp Token Validation

Cinque **Basic Building Blocks (BBB)** applicati al TST:

1. **ISC** — Identificazione certificato TSA
2. **XCV** — Validazione catena X.509 del TSA (vs TrustedList)
3. **CV** — Verifica crittografica del token
4. **SAV** — Accettazione firma
5. **AOV** — Obsolescenza algoritmi

Output: `PASSED` / `INDETERMINATE` / `FAILED` con `SubIndication`.

### SubIndication rilevanti per timestamp

| SubIndication | Scenario |
|---|---|
| `NO_CERTIFICATE_CHAIN_FOUND` | Catena TSA non costruibile da TrustedList |
| `NO_CERTIFICATE_CHAIN_FOUND_NO_POE` | Trust anchor non fidato, nessuna POE |
| `REVOKED_NO_POE` | Certificato TSA revocato, tempo firma indeterminabile |
| `OUT_OF_BOUNDS_NO_POE` | Certificato TSA scaduto, validità temporale non confermabile |
| `CRYPTO_CONSTRAINTS_FAILURE_NO_POE` | Algoritmo TSA sotto soglia sicurezza (TS 119 312 V1.5.1) |
| `HASH_FAILURE` | Message imprint nel TST non corrisponde al dato firmato |
| `SIG_CRYPTO_FAILURE` | Firma crittografica del token TSA non verificabile |
| `TIMESTAMP_ORDER_FAILURE` | Ordine temporale timestamp non rispettato |
| `NO_POE` | Mancanza di proof of existence |

## §5.5 — CAdES-T / CAdES-LT

Sequenza:
1. Validazione base (§5.3)
2. Per ogni signature timestamp: §5.4 → se PASSED, produce POE(production_time)
3. `best-signature-time` = min(production_time di TST PASSED)
4. Validazione revocation con best-signature-time come reference time
5. Cryptographic constraints SAV a best-signature-time

**Semantica POE:** Un TST PASSED aggiorna `best-signature-time`, permettendo di risolvere SubIndication `*_NO_POE` nella catena del firmatario.

**TSA non trusted:** Se §5.4 → INDETERMINATE per il signature timestamp, quel TST non contribuisce POE. Se è l'unico TST, `best-signature-time` rimane al tempo corrente → validazione storica impossibile.

## §5.6 — CAdES-LTA

Sequenza (dopo §5.5):
1. Valida Evidence Records (RFC 4998/6283)
2. Processa archive timestamps **newest-first** via §5.4
3. Ogni archive TST PASSED contribuisce POE per tutto il materiale che include
4. Past signature validation: rivalidazione firma con POE storiche
5. Delay validation
6. SAV con cryptographic constraints al best-signature-time aggregato

**Catena archive timestamps:** POE da TST recente copre oggetti inclusi in TST precedente.

**Archive TST invalido (FAILED):**
- Se constraint = WARN → ignorato, si continua
- Se constraint = FAIL → l'intera validazione ritorna SubIndication del TST fallito

## Aggregazione indicazioni per TSD (timestamp multipli)

1. Ogni timestamp passa §5.4 → `PASSED` / `INDETERMINATE` / `FAILED`
2. TST PASSED → contribuisce POE
3. TST FAILED/INDETERMINATE → ignorato (WARN) o propaga fallimento (FAIL)
4. POE aggregate risolvono `*_NO_POE` della firma principale
5. Result finale della firma dipende da tutti i building blocks

## Implementazione in DSS

Classi DSS (dss-validation module):
- `ValidationProcessForBasicSignatures` — §5.3
- `TimestampValidationProcess` — §5.4  
- `ValidationProcessForSignaturesWithLongTermValidationData` — §5.5
- `ValidationProcessForSignaturesWithArchivalData` — §5.6

SubIndication enum: `eu.europa.esig.dss.enumerations.SubIndication` (mappato 1:1 con §5.4).

## Related

- [[concepts/dss-timestamp-api]] — come leggere i risultati da Reports
- [[concepts/baseline-profiles]] — livelli B/T/LT/LTA
- [[concepts/trusted-lists]] — XCV step del §5.4 usa TrustedList
- [[concepts/revocation-data]] — usato in §5.5 per validazione certificate TSA
- [[concepts/reports]] — Reports DSS espone indication/subIndication
