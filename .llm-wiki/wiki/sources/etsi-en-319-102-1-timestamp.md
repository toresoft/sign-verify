---
type: source
title: "ETSI EN 319 102-1 V1.4.1 — §5.4-§5.6 timestamp validation algorithm"
slug: etsi-en-319-102-1-timestamp
status: ingested
created: 2026-06-27
updated: 2026-06-27
category: standards
urls:
  - https://www.etsi.org/deliver/etsi_en/319100_319199/31910201/01.04.01_60/en_31910201v010401p.pdf
credibility: high
---

# ETSI EN 319 102-1 V1.4.1 — Validazione timestamp AdES

Standard normativo (giugno 2024). Implementato da DSS (Commissione Europea).

## §5.4 — Time-stamp Token Validation BBB

5 Building Blocks per ogni TST: ISC, XCV (catena TSA vs TrustedList), CV (crypto), SAV, AOV (algoritmi obsoleti).

SubIndication rilevanti: `NO_CERTIFICATE_CHAIN_FOUND`, `NO_CERTIFICATE_CHAIN_FOUND_NO_POE`, `REVOKED_NO_POE`, `OUT_OF_BOUNDS_NO_POE`, `CRYPTO_CONSTRAINTS_FAILURE_NO_POE`, `HASH_FAILURE`, `SIG_CRYPTO_FAILURE`, `TIMESTAMP_ORDER_FAILURE`.

## §5.5 — CAdES-T / CAdES-LT

Ogni signature TST passa §5.4 → se PASSED produce POE(production_time). `best-signature-time = min(POE)`. POE risolve `*_NO_POE` nella catena del firmatario.

## §5.6 — CAdES-LTA

Archive TST validati newest-first via §5.4. Catena di POE. Con constraint WARN: TST invalido ignorato; con constraint FAIL: propaga SubIndication.

## Implementazione DSS

- `TimestampValidationProcess` → §5.4
- `ValidationProcessForSignaturesWithLongTermValidationData` → §5.5
- `ValidationProcessForSignaturesWithArchivalData` → §5.6

`SubIndication` enum DSS: mappato 1:1 con §5.4. Cryptographic constraints: TS 119 312 V1.5.1 (dicembre 2024).
