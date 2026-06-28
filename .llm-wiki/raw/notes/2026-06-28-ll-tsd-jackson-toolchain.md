---
title: "Lessons Learned: TSD impl, Jackson CVE, JDK-25 toolchain"
type: lessons-learned
source: session
date: 2026-06-28
tags: [lessons-learned, dss, tsd, jackson, spotless, jdk25, maven, security]
lesson_count: 5
category: notes
confidence: high
summary: "RFC 5544 imprint resolution, DSS-vs-BC routing, Jackson CVE pinning, and JDK-25 build-toolchain gotchas (Spotless/JaCoCo)."
---

# Lessons Learned: TSD impl, Jackson CVE, JDK-25 toolchain

> Extracted from session on 2026-06-28. 5 lessons from implementing RFC 5544 `.tsd` verification, patching a Jackson CVE, and building under JDK 25.

## Lesson 1: RFC 5544 TSD timestamp imprint is not always sha512(getContent())

**Category**: gotcha
**Context**: Validating `sample-rfc5544.tsd` (FreeTSA token, from CPAN `Crypt::TimestampedData`) via DSS `DetachedTimestampValidator`.
**Symptom**: DSS logged `Digest in TimestampToken matches digest of extracted data from document: false` → overall `FAILED`; even BC's own `CMSTimeStampedData.validate(dcp, getContent())` threw `ImprintDigestInvalidException`.
**Root cause**: The producer wrapped the payload in a CMS `id-data` ContentInfo and timestamped the **inner 325-byte octets**, not the 348-byte `getContent()` wrapper. The imprint covers different bytes than the container's `content` field.
**Fix**: `resolveTimestampedContent()` — hash `getContent()` first (normal Aruba/GoSign case), then peel one CMS layer, selecting whichever candidate's digest equals `token.getTimeStampInfo().getMessageImprintDigest()` (compared with BC `BcDigestCalculatorProvider.get(hashAlg)`); honest `HASH_FAILURE` if none match.
**Rule**: Verify a timestamp against the bytes whose hash actually equals the token imprint — recompute and match candidates, don't assume the container's declared content field is what was stamped.

## Lesson 2: DSS 6.4 has no factory for RFC 5544 TSD — route via Bouncy Castle

**Category**: discovery
**Context**: `/verifications` rejected `.tsd` files.
**Symptom**: `SignedDocumentValidator.fromDocument()` → `IllegalInputException: A CMS file is expected : Not a valid CAdES file`, surfaced as `signature.parse-error`.
**Root cause**: `id-aa-timeStampedData` (OID 1.2.840.113549.1.9.16.1.31) is not `id-signedData`; DSS 6.4 has no validator for it. `CMSDocumentAnalyzer.isSupported()` returns true (byte 0x30) but the signature build throws.
**Fix**: `TsdAwareValidatorAdapter` `@Primary` decorator over `DssValidatorAdapter` — catches only `Errors.SIGNATURE_PARSE_ERROR`, retries with `new CMSTimeStampedData(bytes)`, validates inner CAdES (if any) + wrapper RFC 3161 tokens via `DetachedTimestampValidator`, strict worst-of aggregation.
**Rule**: When DSS lacks a factory for a CMS content type, decorate the validator port and fall back to Bouncy Castle for that one content type rather than polluting the CAdES path.

## Lesson 3: Jackson CVE — when the minimal patch isn't on Central, jump to the next minor

**Category**: rule
**Context**: Spring Boot 3.5.14 manages `jackson-databind` 2.21.2, flagged for CVE-2026-23183/54512/54513/54515.
**Symptom**: CVE-2026-54515 is fixed in 2.21.5, but `repo.maven.apache.org/.../jackson-databind/2.21.5/...pom` → HTTP 404 (only 2.21.4 then 2.22.0 exist).
**Root cause**: The advisory's named patch version was never published to Maven Central for that branch.
**Fix**: Override `<jackson-bom.version>2.22.0</jackson-bom.version>` (first released version > 2.21.4 clearing all four); verified resolution + tests.
**Rule**: Before pinning a security-fix version, confirm it exists on Central (`curl maven-metadata.xml` / probe the `.pom`); if the named patch is absent, pin the next released minor that supersedes the fix.

## Lesson 4: Spotless google-java-format breaks on JDK 25 — bump GJF, don't downgrade the JVM

**Category**: gotcha
**Context**: `mvn spotless:apply` under JetBrains JBR (Java 25).
**Symptom**: `NoSuchMethodError: 'java.util.Queue com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()'` — fails on every file, even pre-existing ones.
**Root cause**: google-java-format 1.24.0 calls a javac internal API removed in JDK 25; not fixable via `--add-exports`.
**Fix**: Bump GJF 1.24.0 → 1.28.0 (`spotless-maven-plugin` `<googleJavaFormat><version>`). 1.28.0 runs on JDK 21 and 25; `spotless:check` on the already-formatted tree showed zero diffs → no reformatting churn.
**Rule**: google-java-format binds to javac internals — pin it to a version that supports your build JVM's major; verify with `spotless:check` (zero-diff) before committing a bump.

## Lesson 5: Building this repo under JDK 25 — JaCoCo off, Temurin 21 for full verify

**Category**: gotcha
**Context**: No SDKMAN/`mvn`/`java` on PATH despite CLAUDE.md; only JetBrains JBR (Java 25) + IntelliJ-bundled Maven 3.9.11 available.
**Symptom**: JaCoCo 0.8.12 → `IllegalArgumentException: Unsupported class file major version 69` instrumenting JDK internals; ArchUnit logs the same and falls back.
**Root cause**: JaCoCo/ASM in the build don't support class-file major 69 (JDK 25).
**Fix**: For quick test runs use `JAVA_HOME=<JBR> mvn -o -Djacoco.skip=true -Dtest=... test`. For full `mvn verify` (Spotless check + JaCoCo report + Failsafe IT) downloaded Temurin 21 (`adoptium.net/v3/binary/latest/21/...`) and ran with `JAVA_HOME=/tmp/jdk21` — BUILD SUCCESS, 192 unit + 15 IT. Docker present for Testcontainers.
**Rule**: When the documented toolchain is missing, JetBrains Toolbox apps ship a usable JDK + Maven; but coverage/format plugins lag new JDK majors, so keep a matching-LTS JDK (21) for `verify`.
