---
type: source
title: "Lessons Learned: TSD imprint, DSS routing, Jackson CVE, JDK-25 toolchain"
slug: ll-tsd-jackson-toolchain
status: insight
created: 2026-06-29
updated: 2026-06-29
category: implementation-lessons
confidence: high
verified: 2026-06-29
volatility: warm
sources:
  - notes/2026-06-28-ll-tsd-jackson-toolchain
---

# Lessons Learned — TSD + Jackson + JDK-25

5 lezioni estratte dall'implementazione del supporto RFC 5544 TSD e dalla gestione del build environment.

## L1: TSD — l'imprint del timestamp non copre sempre `getContent()`

**Contesto**: `DetachedTimestampValidator` su `sample-rfc5544.tsd` (FreeTSA, da CPAN `Crypt::TimestampedData`).
**Sintomo**: DSS logga `Digest in TimestampToken matches digest of extracted data from document: false` → FAILED; anche BC `CMSTimeStampedData.validate(dcp, getContent())` lancia `ImprintDigestInvalidException`.
**Root cause**: il producer ha incapsulato il payload in un `ContentInfo id-data` e ha timestampato i **325 byte inner**, non i 348 byte `getContent()`. L'imprint copre byte diversi dal `content` field del container.
**Fix**: `resolveTimestampedContent()` — prova prima `getContent()` (caso Aruba/GoSign normale), poi pela un layer CMS, selezionando il candidato il cui digest == `token.getTimeStampInfo().getMessageImprintDigest()` (confronto con BC `BcDigestCalculatorProvider.get(hashAlg)`); `HASH_FAILURE` onesto se nessuno corrisponde.
**Regola**: verifica sempre il timestamp contro i byte il cui hash è nel token imprint — ricomputa e confronta candidati, non assumere che il `content` field del container sia quello che è stato stampato.

## L2: DSS 6.4 non ha factory per RFC 5544 TSD — routing via Bouncy Castle

**Contesto**: `/verifications` rigettava i file `.tsd`.
**Sintomo**: `SignedDocumentValidator.fromDocument()` → `IllegalInputException: A CMS file is expected : Not a valid CAdES file`.
**Root cause**: `id-aa-timeStampedData` (OID `1.2.840.113549.1.9.16.1.31`) ≠ `id-signedData`; DSS 6.4 non ha un validator per esso. `CMSDocumentAnalyzer.isSupported()` ritorna `true` (byte `0x30`) ma la costruzione del validator lancia.
**Fix**: `TsdAwareValidatorAdapter` `@Primary` decorator su `DssValidatorAdapter` — intercetta solo `Errors.SIGNATURE_PARSE_ERROR`, riprova con `new CMSTimeStampedData(bytes)`, valida il CAdES inner (se presente) + i wrapper RFC 3161 token via `DetachedTimestampValidator`, aggregazione worst-of stretta.
**Regola**: quando DSS non ha factory per un content type CMS, decorare il validator port e delegare a Bouncy Castle solo per quel tipo, senza inquinare il path CAdES principale.

## L3: Jackson CVE — se la patch non è su Central, salta al prossimo minor

**Contesto**: Spring Boot 3.5.14 gestisce `jackson-databind` 2.21.2; CVE-2026-23183/54512/54513/54515.
**Sintomo**: CVE-2026-54515 è risolto in 2.21.5 ma `repo.maven.apache.org/.../jackson-databind/2.21.5/...pom` → HTTP 404 (solo 2.21.4 e 2.22.0 esistono).
**Root cause**: la versione patch nominata nell'advisory non è mai stata pubblicata su Maven Central per quel branch.
**Fix**: `<jackson-bom.version>2.22.0</jackson-bom.version>` (primo released > 2.21.4 che copre tutti e quattro i CVE); verificato con risoluzione + test.
**Regola**: prima di pinnare una versione security-fix, confermare che esista su Central (`curl maven-metadata.xml` o probe del `.pom`); se assente, pinnare il prossimo released minor che supera il fix.

## L4: Spotless/google-java-format si rompe su JDK 25 — aggiornare GJF, non il JVM

**Contesto**: `mvn spotless:apply` con JetBrains JBR (Java 25).
**Sintomo**: `NoSuchMethodError: 'java.util.Queue com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()'` — fallisce su ogni file.
**Root cause**: google-java-format 1.24.0 chiama una API interna di javac rimossa in JDK 25; non risolvibile con `--add-exports`.
**Fix**: GJF 1.24.0 → 1.28.0 (campo `<googleJavaFormat><version>` nel plugin Spotless). 1.28.0 gira su JDK 21 e 25; `spotless:check` sull'albero già formattato → zero diff → nessun churn.
**Regola**: google-java-format si lega alle API interne di javac — pinnarlo a una versione che supporta il major JVM del build; verificare con `spotless:check` (zero-diff) prima di committare il bump.

## L5: Build JDK-25 in questo repo — JaCoCo disabilitato, Temurin 21 per verify completo

**Contesto**: niente SDKMAN/`mvn`/`java` sul PATH nonostante CLAUDE.md; solo JetBrains JBR (Java 25) + Maven 3.9.11 bundled IntelliJ.
**Sintomo**: JaCoCo 0.8.12 → `IllegalArgumentException: Unsupported class file major version 69`; ArchUnit stessa eccezione con fallback.
**Root cause**: JaCoCo/ASM nel build non supportano class-file major 69 (JDK 25).
**Fix quick**: `JAVA_HOME=<JBR> mvn -o -Djacoco.skip=true -Dtest=... test`.
**Fix full verify**: Temurin 21 scaricato (`adoptium.net/v3/binary/latest/21/...`), `JAVA_HOME=/tmp/jdk21` → BUILD SUCCESS, 192 unit + 15 IT.
**Regola**: quando la toolchain documentata manca, IntelliJ/JBR portano JDK + Maven usabili; ma plugin di coverage/format lagano i nuovi JDK major → tenere un JDK LTS (21) per `verify`.

## See Also

- [[concepts/rfc5544-tsd]] — imprint resolution, DSS routing TSD
- [[entities/dssvalidatoradapter]] — `TsdAwareValidatorAdapter` decorator
- [[entities/sign-verify-2]] — service overview
