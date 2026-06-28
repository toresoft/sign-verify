---
type: source
title: "Corpus e generazione file .tsd (RFC 5544) per test — ricerca multi-agente"
slug: tsd-test-corpus-research
status: ingested
created: 2026-06-28
updated: 2026-06-28
category: testing
urls:
  - https://metacpan.org/release/BRUGNARA/Crypt-TimestampedData-0.01-TRIAL
  - https://github.com/bcgit/bc-java/blob/master/pkix/src/test/java/org/bouncycastle/tsp/test/CMSTimeStampedDataGeneratorTest.java
  - https://www.freetsa.org/index_en.php
  - https://gist.github.com/Manouchehri/fd754e402d98430243455713efada710
  - https://www.jimby.name/techbits/recent/openssl_tsa/
  - https://docs.openssl.org/3.1/man1/openssl-ts/
credibility: high
---

# Corpus e generazione `.tsd` per test

_Ricerca (2026-06-28) sulla domanda: «dove trovare o come generare file `.tsd` (RFC 5544) per i test»._

> ⚠️ **Correzione a [[analyses/verifica-file-tsd]] §"Test vectors":** la precedente affermazione
> «non esistono corpus `.tsd` pubblici» è **parzialmente smentita**. Esiste almeno un fixture
> pubblico reale (CPAN, sotto) e percorsi riproducibili per generarne quanti se ne vuole.

## 1. Fixture pubblico reale — CPAN `Crypt::TimestampedData` (credibilità: alta)

- Release: https://metacpan.org/release/BRUGNARA/Crypt-TimestampedData-0.01-TRIAL
- Tarball: https://cpan.metacpan.org/authors/id/B/BR/BRUGNARA/Crypt-TimestampedData-0.01-TRIAL.tar.gz
- Autore: Guido Brugnara (IT). Licenza Perl (GPL v1 / Artistic) → riusabile come fixture con attribuzione.
- Contiene `test_output.tsd` (5893 byte): **RFC 5544 TSD valido verificato**:
  - `ContentInfo` contentType `1.2.840.113549.1.9.16.1.31` (id-aa/id-ct-timestampedData)
  - `TimeStampedData` v1, metaData fileName `test_data.txt`, content OCTET STRING (CMS pkcs7-data interno)
  - `temporalEvidence` = pkcs7-signedData con `id-smime-ct-TSTInfo` — token **FreeTSA** (`O=Free TSA`,
    `www.freetsa.org`), SHA-512, istante 2025-09-27T13:20:41Z; catena TSA embeddata.
  - **Verificato localmente** con `openssl asn1parse` (OID e struttura confermati).
- La distribuzione include script CLI (`tsd-create`, `tsd-timestamp`, `tsd-extract`, `tsd-info`) che
  fanno POST a `https://freetsa.org/tsr` → genera fixture freschi senza account Aruba/GoSign.
- Copia salvata: `scratchpad/freetsa-sample.tsd` (+ `test_data.txt`) in questa sessione.

**Nota:** questo TSD ha `content` = pkcs7-**data** semplice (non un `.p7m` CAdES firmato). Per il caso
PA reale serve un TSD che avvolge un CAdES `.p7m` → generarlo (sezione 2/3).

## 2. Generazione programmatica in Java con Bouncy Castle (credibilità: alta)

Classi `org.bouncycastle.tsp.cms` (bcpkix, già nel classpath 1.84):
- `CMSTimeStampedDataGenerator` — `setMetaData(boolean hashProtected, String fileName, String mediaType)`,
  `setDataUri(URI)`, `initialiseMessageImprintDigestCalculator(DigestCalculator)`,
  `generate(TimeStampToken token, byte[] content)` (+ overload InputStream).
- `CMSTimeStampedData` — `getEncoded()` (→ byte del `.tsd`), `getContent()`, `getTimeStampTokens()`,
  `addTimeStamp(TimeStampToken)` (chaining/renewal), `calculateNextHash(DigestCalculator)`,
  `validate(DigestCalculatorProvider, byte[] dataDigest[, TimeStampToken])`.
- Digest SHA-256: `new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256))`.
- Supporto RFC 5544 in BC dal 1.46.

Token RFC 3161 di test **self-issued** (offline, no TSA reale — da `NewTSPTest.java`):
- Keypair/cert test (`TSPTestUtil.makeKeyPair()`/`makeCACertificate()` sono helper di test BC, da replicare).
- `TimeStampTokenGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC").build("SHA256withRSA", key, cert), digestCalc, policyOID)` + `addCertificates(certs)`.
- `TimeStampResponseGenerator(tokenGen, TSPAlgorithms.ALLOWED).generate(req, serial, new Date()).getTimeStampToken()`.

Ricetta fixture: hash del `.p7m` esistente → mint token con TSA di test → `gen.generate(token, p7mBytes)` →
`Files.write(path, tsd.getEncoded())`. Fonte: `CMSTimeStampedDataGeneratorTest.java`, `NewTSPTest.java` (bc-java).

## 3. TSA pubbliche gratuite per token RFC 3161 reali (credibilità: alta)

- **FreeTSA**: `https://freetsa.org/tsr`, POST `application/timestamp-query`, no registrazione, sha1..sha512.
  CA/TSA cert scaricabili (`files/cacert.pem`, `files/tsa.crt`) per la verifica.
- Lista TSA gratuite (gist Manouchehri): DigiCert `http://timestamp.digicert.com`, Sectigo
  `http://timestamp.sectigo.com`, Certum, GlobalSign, Entrust.
- **TSA EU qualificate** (chain verso Trusted List → utili per test con LOTL/TSL): BaltStamp
  `http://tsa.baltstamp.lt`, Belgio `http://tsa.belgium.be/connect`, QuoVadis `http://ts.quovadisglobal.com/eu`.
- TSA italiane qualificate (InfoCert/Aruba/Namirial) richiedono account a pagamento — AgID elenco QTSP.

## 4. Tool PA italiani che emettono `.tsd` nativamente (credibilità: media)

- **GoSign Desktop (Free, InfoCert)**: "MARCA" → formato **TSD** (default nel free). Verifica anche .tsd/.m7m/.tsr.
- **ArubaSign** e **Dike 6**: "Marca" → selezionare formato TSD. File self-contained (documento + token).
- Catch comune: software gratis, ma serve un credito **marca temporale** a pagamento per timbrare davvero.
- Uso: generare 1 fixture "reale" con provenienza PA (lotto marche low-cost) da committare come risorsa statica.

## 5. OpenSSL: cosa può e non può (credibilità: alta)

- **PUÒ** RFC 3161: `openssl ts -query -data f -sha256 -out f.tsq`; TSA locale self-signed
  (`openssl ts -reply -queryfile f.tsq -inkey tsakey.pem -signer tsacert.pem -out f.tsr`);
  token nudo `-token_out`; verifica `-verify`; ispezione `-reply -in f.tsr -text`.
  Cert TSA: `extendedKeyUsage = critical, timeStamping`. Ricetta TSA completa: jimby.name.
- **NON PUÒ** RFC 5544: nessun comando crea/valida un `.tsd`. Solo ispezione:
  `openssl asn1parse -inform DER -in f.tsd` → cercare OID `1.2.840.113549.1.9.16.1.31`, poi
  `-strparse <offset>` per scendere nel token RFC 3161 annidato. Il "ponte" per costruire un `.tsd`
  resta Bouncy Castle (sezione 2) o DSS.

## ASN.1 di riferimento (RFC 5544)

```asn1
ContentInfo { contentType = id-ct-timestampedData (1.2.840.113549.1.9.16.1.31),
              content [0] TimeStampedData }
TimeStampedData ::= SEQUENCE { version INTEGER {v1(1)}, dataUri IA5String OPTIONAL,
              metaData MetaData OPTIONAL, content OCTET STRING OPTIONAL,
              temporalEvidence Evidence }
Evidence ::= CHOICE { tstEvidence [0] TimeStampTokenEvidence, ... }
TimeStampTokenEvidence ::= SEQUENCE SIZE(1..MAX) OF TimeStampAndCRL
TimeStampAndCRL ::= SEQUENCE { timeStamp TimeStampToken, crl CertificateList OPTIONAL }
```

## Related
- [[analyses/tsd-test-corpus]] — playbook generazione fixture per sign-verify-2
- [[analyses/verifica-file-tsd]] — routing/validazione `.tsd` (correzione test-vectors)
- [[concepts/rfc5544-tsd]] — formato TSD
- [[concepts/timestamping]] — RFC 3161
- [[concepts/trusted-lists]] — TSA qualificate / LOTL
