---
title: "Lessons Learned: running the DSS validation tests offline (717s → 40s)"
type: lessons-learned
source: session
date: 2026-06-30
tags: [lessons-learned, dss, testing, certificate-verifier, aia, revocation, spring-boot, junit, maven]
lesson_count: 5
category: notes
confidence: high
summary: "Cutting SivaCorpus test runtime from 717s to 40s: AIA (not OCSP/CRL) was the network cost because CommonCertificateVerifier seeds a DefaultAIASource by default; plus diagnose-before-rerun, mvn -q hides surefire output, global test-profile @Primary verifier override, and @Tag exclusion on both surefire and failsafe."
---

# Lessons Learned: running the DSS validation tests offline (717s → 40s)

> Extracted from session on 2026-06-30. 5 lessons from making `SivaCorpusValidationTest`
> (180+ fixtures) and the other DSS `@SpringBootTest` classes run offline.
> See [[entities/certificateverifier]], [[concepts/revocation-data]],
> [[entities/dssvalidatoradapter]], [[sources/ll-tsd-jackson-toolchain]].

## Lesson 1: AIA — not OCSP/CRL — was the network cost; CommonCertificateVerifier seeds a DefaultAIASource by default

**Category**: discovery
**Context**: DSS validation tests ran ~717s. Hypothesis: online revocation (OCSP/CRL) fetches per signature over 180 untrusted foreign certs.
**Symptom**: Nulling `OcspSource` and `CrlSource` on the test `CertificateVerifier` changed nothing — still ~717s.
**Root cause**: `CommonCertificateVerifier` creates a `DefaultAIASource` **by default even if you never call `setAIASource(...)`**. AIA (Authority Information Access) issuer-certificate fetching was firing one HTTP round-trip per signature against slow/dead endpoints — the dominant cost, independent of revocation.
**Fix**: `cv.setAIASource(null)` explicitly (plus null OCSP/CRL, `setRevocationFallback(false)`). Runtime dropped to ~40s.
**Rule**: To make DSS validation fully offline you must null the AIA source explicitly — a fresh `CommonCertificateVerifier` is not network-free out of the box; dropping only OCSP/CRL is insufficient. Verify getters: `getOcspSource()/getCrlSource()/getAIASource()/isRevocationFallback()`.

## Lesson 2: diagnose the wired bean empirically before paying for a 12-minute re-run

**Category**: pattern
**Context**: First "fix" still took 717s; re-running the full suite to test each guess costs ~12 min.
**Root cause**: Guessing at why ocsp/crl-null didn't help, instead of observing the actual verifier state.
**Fix**: Wrote a throwaway `@SpringBootTest` that autowired the `CertificateVerifier` and printed `getOcspSource()/getCrlSource()/getAIASource()` plus per-fixture timing on 5 files. Output `ocsp=null crl=null aia=DefaultAIASource@...` pinpointed AIA in ~30s instead of a 12-min blind re-run.
**Rule**: When a slow integration suite resists a fix, spend 30s on a tiny probe that prints the live object graph and times a few items — never re-run the whole expensive suite to test a hypothesis.

## Lesson 3: `mvn -q ... | tail` swallows surefire results; on-disk reports are the source of truth

**Category**: gotcha
**Context**: A backgrounded `mvn -q test ... | tail -40` produced an empty output file; the `BUILD SUCCESS` line is INFO-level and suppressed by `-q`.
**Symptom**: Empty log; later read a `surefire-reports/*.txt` showing 717s and mistook a **stale** report (from a morning run) for the current result. Also two overlapping `mvn` runs clobbered the shared `target/surefire-reports/` dir.
**Root cause**: `-q` suppresses the reactor summary; piped stdout is buffered until process exit; surefire reports are per-class files overwritten by any concurrent run.
**Fix**: Run without `-q`, redirect to a controlled log, gate completion on a self-emitted `DONE exit=$? wall=...s` marker, and check report **mtimes** against wall-clock to tell fresh from stale. Never run two builds against the same target dir concurrently.
**Rule**: For scripted Maven runs, drop `-q`, capture to a known log with an explicit completion marker, and treat `surefire-reports/*.txt` mtime — not its mere presence — as proof of a fresh run.

## Lesson 4: a global test-profile `@Primary` bean override is picked up by every `@SpringBootTest`

**Category**: pattern
**Context**: Needed one offline `CertificateVerifier` to apply across all DSS integration tests without editing each.
**Root cause**: `@SpringBootTest` uses the app's `@SpringBootApplication` component scan (base `org.toresoft.signverify`); compiled test classes live on the classpath under that same base package, so a `@Configuration` in `src/test/java/.../config` is scanned too.
**Fix**: `OfflineDssTestConfig` — `@Configuration @Profile("test")` with `@Bean @Primary CertificateVerifier`. `@Primary` wins by-type injection into both `DssValidatorAdapter` and `TsdAwareValidatorAdapter` (both consume the injected verifier). Give it a distinct bean/method name so it adds a candidate rather than colliding with the production bean name.
**Rule**: To override a production bean for all integration tests of a profile, put a `@Configuration @Profile("<p>") @Bean @Primary` (distinct name) in test sources under the scanned base package — no per-test `@Import` needed.

## Lesson 5: JUnit `@Tag` exclusion must be set on both Surefire and Failsafe

**Category**: gotcha
**Context**: Added `DssRevocationNetworkIT @Tag("network")` to keep one real-network test, excluded from the default build.
**Root cause**: `<excludedGroups>` is per-plugin; setting it on only one of Surefire (unit `*Test`) / Failsafe (`*IT`) leaks the tagged tests into the other phase.
**Fix**: `<excludedGroups>network</excludedGroups>` on **both** maven-surefire-plugin and maven-failsafe-plugin. Opt in with `mvn verify -Dgroups=network`.
**Rule**: Tag-based test exclusion in Maven must be configured on every test-running plugin (Surefire and Failsafe); a default-excluded, opt-in `@Tag("network")` IT is the clean way to keep live-network coverage out of CI.
