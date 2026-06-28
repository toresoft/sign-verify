---
type: analysis
created: 2026-06-28
updated: 2026-06-28
query: "dove trovare o come generare file .tsd per i test di sign-verify-2"
sources:
  - sources/tsd-test-corpus-research
  - sources/rfc5544-tsd-standard
---

# Corpus `.tsd` per i test — playbook

_Risposta alla domanda: «come procurarsi file `.tsd` (RFC 5544) per testare il supporto TSD»._

## Sintesi

Tre vie, in ordine di utilità per la CI di sign-verify-2:

1. **Generazione programmatica (Bouncy Castle)** — *consigliata come fixture primario.* Riproducibile,
   offline, nessun account. Avvolgere un `.p7m` CAdES di test esistente in un `CMSTimeStampedData` con
   un token RFC 3161 self-issued. Copre il caso PA reale (TSD su CAdES). Vedi [[analyses/verifica-file-tsd]].
2. **Fixture pubblico reale (CPAN)** — `test_output.tsd` da `Crypt::TimestampedData` (token FreeTSA,
   licenza Perl). Utile come fixture "esterno reale" per il routing/parse, ma il suo `content` è
   pkcs7-data semplice, non un CAdES firmato.
3. **Tool PA (GoSign Free / ArubaSign / Dike)** — un `.tsd` "reale" con provenienza PA italiana, da
   committare come risorsa statica. Richiede credito marca temporale a pagamento (one-off).

## Via 1 — generare il fixture in test (consigliata)

Pseudo-flusso per un test fixture builder (BC `org.bouncycastle.tsp.cms`):

```java
// 1. TSA di test self-issued (replicare TSPTestUtil di bc-java)
KeyPair tsaKp = ...; X509Certificate tsaCert = selfSignedTimeStamping(tsaKp);

// 2. token RFC 3161 sul digest del .p7m esistente
DigestCalculator sha256 =
    new BcDigestCalculatorProvider().get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
byte[] p7m = Files.readAllBytes(existingCadesP7m);
TimeStampToken token = mintToken(tsaKp, tsaCert, sha256, p7m); // TimeStampResponseGenerator...

// 3. wrap RFC 5544
CMSTimeStampedDataGenerator gen = new CMSTimeStampedDataGenerator();
gen.setMetaData(false, "test.p7m", "application/pkcs7-mime");
gen.initialiseMessageImprintDigestCalculator(sha256);
CMSTimeStampedData tsd = gen.generate(token, p7m);
Files.write(tsdPath, tsd.getEncoded());   // → fixture .tsd
```

Casi corpus da generare (allineati al piano, [[analyses/tsd-dto-mapping]]):
- TSD su `.p7m` CAdES-T → firma + marca PASSED.
- TSD su `.p7m` multi-firma → `signaturesCount=2`.
- TSD su `.p7m` con controfirma.
- TSD con token la cui TSA NON è in Trusted List → `INDETERMINATE / NO_CERTIFICATE_CHAIN_FOUND` sulla marca.
- (Routing) TSD "puro" CPAN (content non firmato) → l'inner non è CAdES: asserire la gestione del caso.

## Via 2 — fixture pubblico reale

- `Crypt-TimestampedData-0.01-TRIAL.tar.gz` → `test_output.tsd` (5893 B), token FreeTSA SHA-512.
- Verifica struttura: `openssl asn1parse -inform DER -in file.tsd` → OID `1.2.840.113549.1.9.16.1.31`.
- Catena: scaricare CA FreeTSA (`https://freetsa.org/files/cacert.pem`) per la validazione del token.

## Via 3 — token reale da TSA gratuita + wrap

- Token RFC 3161 gratis: `openssl ts -query -data f.p7m -sha256 -cert -out f.tsq` →
  `curl -H 'Content-Type: application/timestamp-query' --data-binary @f.tsq https://freetsa.org/tsr -o f.tsr`.
- OpenSSL **non** costruisce il `.tsd`: estrarre il token dal `.tsr` e avvolgerlo con BC (Via 1, passo 3).
- TSA EU **qualificate** (per test chain→Trusted List): BaltStamp, Belgio, QuoVadis EU.

## Validazione di un fixture generato

`CMSTimeStampedData.validate(digCalcProvider, dataDigest[, token])` lancia `ImprintDigestInvalidException`
se l'imprint non corrisponde → utile per asserire che il fixture è ben formato prima di darlo a DSS.

## Note

- `.tsd` è un formato de-facto italiano; globalmente si usano token RFC 3161 nudi o ASiC ([[concepts/rfc5544-tsd]]).
- OpenSSL utile solo per RFC 3161 e per **ispezionare** un `.tsd` (`asn1parse`/`-strparse`), non per crearlo.

## Fonti
- Ricerca corpus/generazione — [[sources/tsd-test-corpus-research]]
- RFC 5544/5955 — [[sources/rfc5544-tsd-standard]]

## Related
- [[analyses/verifica-file-tsd]] · [[analyses/tsd-dto-mapping]]
- [[concepts/rfc5544-tsd]] · [[concepts/timestamping]] · [[concepts/trusted-lists]]
- [[entities/detachedtimestampvalidator]] · [[entities/signeddocumentvalidator]]
