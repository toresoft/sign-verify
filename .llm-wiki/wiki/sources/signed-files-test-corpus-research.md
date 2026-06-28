---
type: source
title: "Corpus pubblici di file firmati (CAdES .p7m / PAdES PDF) per test — ricerca multi-agente"
slug: signed-files-test-corpus-research
status: ingested
created: 2026-06-28
updated: 2026-06-28
category: testing
urls:
  - https://github.com/esig/dss/tree/master/dss-pades/src/test/resources/validation
  - https://github.com/esig/dss/tree/master/dss-cades/src/test/resources/validation
  - https://github.com/open-eid/SiVa-Test
  - https://github.com/open-eid/digidoc4j
  - https://docs.openssl.org/3.1/man1/openssl-cms/
credibility: high
---

# Corpus file firmati (CAdES / PAdES) per test funzionali

_Ricerca (2026-06-28): dove trovare `.p7m` (CAdES) e PDF firmati (PAdES) scaricabili per i test._

## ⚠️ Correzione path DSS (verificata via curl 2026-06-28)

- I vecchi path `dss-cades/.../plugtest/cades/CAdES-BES/Sample_Set_*/Signature-C-BES-*.p7m`
  **non esistono più** (HTTP **404** su `master`). Erano nei risultati dei motori ma sono stati
  rimossi/spostati.
- I corpus **vivi e raw-fetchable** sono le cartelle `.../src/test/resources/validation/` (HTTP **200**
  confermato su `pades-bes.pdf`, `modified_after_signature.pdf`, `cades-bes-signeddata-enveloping.p7m`).

## 1. DSS `dss-pades` validation (PAdES) — credibilità alta, **TOP**

- Folder: https://github.com/esig/dss/tree/master/dss-pades/src/test/resources/validation
- Raw: `https://raw.githubusercontent.com/esig/dss/master/dss-pades/src/test/resources/validation/<file>`
- Stessa libreria che usiamo (DSS 6.4) → comportamento autoritativo. Licenza **LGPL-2.1**.
- ~117 file. Spettro completo (verificati 200):
  - **B**: `pades-bes.pdf`, `pades3_Baseline_B.pdf`, `pades-not-epes.pdf`, `pades-bes-no-certificates.pdf`
  - **T**: `doc-firmado-T.pdf`, `pades-t-level-extended.pdf`, `pades-t-duplicated-sigtst.pdf`
  - **LT/LTV**: `PAdES-LT.pdf`, `pades-ltv.pdf`, `lt-short.pdf`, `belgian_pki_multiple_ocsps_lt.pdf`
  - **LTA**: `PAdES-LTA.pdf` (~4.2 MB), `pades-lta-copied-doctst.pdf`
  - **Multi-firma / TS**: `pades-5-signatures-and-1-document-timestamp.pdf`, `51sigs.pdf`, `timestamped_and_signed.pdf`
  - **Negativi/attacco**: `modified_after_signature.pdf`, `pdf-signed-corrupted.pdf`, `pdf-byterange-overlap.pdf`,
    `pdf-double-signer-info.pdf`, `pdf-spoofing-attack.pdf`, `pades-spoofing-replaced-reason.pdf`,
    `malformed-pades.pdf`, `pades_infinite_loop.pdf` (DoS), `encrypted.pdf`
  - **Interop nazionali (origine plugtest)**: `Signature-P-BG_BOR-1.pdf`, `Signature-P-FR_CS-5.pdf`,
    `Signature-P-HU_POL-3.pdf`, `Signature-P-SK-6.pdf`, `self-issued-qesig.pdf`

## 2. DSS `dss-cades` validation (CAdES) — credibilità alta, **TOP**

- Folder: https://github.com/esig/dss/tree/master/dss-cades/src/test/resources/validation
- Raw: `https://raw.githubusercontent.com/esig/dss/master/dss-cades/src/test/resources/validation/<file>`
- ~72 file (alcune sottocartelle vuote: `dss-1188`, `evidence-record` → saltare). LGPL-2.1.
  - **B**: `cades-bes-signeddata-enveloping.p7m`, `Signature-CBp-B-1.p7m`, `cades-extended-bes.pkcs7`
  - **T**: `cades-t-copied-sigtst.p7m`, `cades-t-duplicated-sigtst.p7m`
  - **LT/LTA**: `Signature-CBp-LT-2.p7m`, `cades-e-lt.p7m`, `Signature-C-B-LTA-10.p7m`, `CAdESDoubleLTA.p7m`
  - **Detached (`.p7s`, serve l'originale)**: `cades-bes-signeddata-detached.p7s`, `signedFile.pdf.p7s`,
    `cades-ats-v3-rev-val-crl.p7s`
  - **Negativi**: `malformed-cades.p7m`, `cades-broken-sig-tst.p7m`, `cades-ats-v3-wrong-cert.p7m`,
    `cms-no-sign-cert.p7m`, `cades-double-signing-certificate.p7m`

## 3. open-eid/SiVa-Test (PAdES EU eID) — credibilità alta, licenza **EUPL-1.1**

- https://github.com/open-eid/SiVa-Test (cartella `src/test/resources/pdf/baseline_profile_test_files/`)
- File EU eID reali: `pades-baseline-t-live-aj.pdf`, `pades-baseline-lta-live-aj.pdf`,
  `hellopades-pades-lt-sha256-sign.pdf`, multi-firma `pades_lt_two_valid_sig.pdf`,
  negativo `hellopades-lt1-lt2-wrongDigestValue.pdf`. Cartelle negative per crypto debole/revoca.
- ⚠️ EUPL-1.1 (copyleft) — verificare i termini prima di committare in repo.

## 4. open-eid/digidoc4j (ASiC/BDOC, **migliori negativi**) — LGPL-2.1

- Invalid (~92): https://github.com/open-eid/digidoc4j/tree/master/digidoc4j/src/test/resources/testFiles/invalid-containers
- Valid (~98): `.../valid-containers`. Include **CAdES-in-ASiC** (`CAdES-baseline-lt.asics`, `-lta`).
- Negativi auto-descrittivi: revocato (`bdoc-tm-ocsp-revoked.bdoc`), cert scaduto
  (`invalid_bdoc21-TS-old-cert.bdoc`), CA sconosciuta (`SS-4_teadmataCA.4.asice`), manomesso
  (`invalid-data-file.bdoc`), TST rotto (`TS_broken_TS.asice`), digest debole (`23200_weakdigest-*`).
- Utile per i casi ASiC + matrice negativa completa.

## 5. Generare `.p7m` (nessun corpus PA italiano pubblico) — openssl cms

- Niente corpus `.p7m` PA italiano scaricabile (AgID/Aruba/Dike solo verifica). Generare:
  - Flag chiave **`-cades`** → aggiunge `signingCertificateV2` (vero CAdES-BES vs PKCS#7 nudo).
  - Enveloping (stile PA `.p7m`): `openssl cms -sign -in doc.pdf -signer cert.pem -inkey key.pem -cades -nodetach -outform DER -binary -out doc.pdf.p7m`
  - Detached `.p7s`: idem senza `-nodetach`.
  - Per CAdES-T/LT/LTA: OpenSSL non basta → usare DSS (`CAdESService` + TSP) o BouncyCastle.
- Cross-check Italia: `unp7m` bundle `ca-italiane.pem`; `eniocarboni/p7m` (GPL-3.0).

## Note licenze
DSS = LGPL-2.1; digidoc4j = LGPL-2.1; SiVa/SiVa-Test = EUPL-1.1. Per fixture di test interni va bene
con attribuzione; verificare EUPL se si redistribuisce.

## Related
- [[analyses/cades-pades-test-corpus]] — catalogo + selezione + script download
- [[analyses/tsd-test-corpus]] — corpus `.tsd`
- [[concepts/ades-signature-formats]] · [[concepts/baseline-profiles]] · [[concepts/signature-validation]]
- [[concepts/reports]] · [[concepts/trusted-lists]]
