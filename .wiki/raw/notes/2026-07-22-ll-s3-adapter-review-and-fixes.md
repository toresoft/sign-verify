---
title: "Lessons Learned: reviewing and fixing the S3 storage adapter"
type: lessons-learned
source: session
date: 2026-07-22
tags: [lessons-learned, code-review, s3-storage, aws-sdk, testcontainers, jacoco, maven, spring-boot-4]
lesson_count: 6
category: notes
confidence: high
summary: "Reviewing an S3 DocumentStoragePort adapter that had been implemented on a feature branch outside the visible session: establishing the real git state before reviewing, catching a design deviation (credentials in app properties) rather than only bugs, and the mechanics of exercising production credential wiring in an IT plus two Maven/JaCoCo gotchas surfaced while verifying the fixes."
---

# Lessons Learned: reviewing and fixing the S3 storage adapter

> Extracted from the 2026-07-22 session that reviewed the `feat/s3-document-storage-adapter`
> branch and resolved all five findings (commit `a242a2f`). 6 lessons, mostly from the
> review/verify loop. Companion to [[2026-07-22-ll-cve-bumps-scaling-s3-adapter]] (the design/plan
> that preceded the implementation).

## Lesson 1: Establish the real git state before reviewing "the changes"

**Category**: gotcha
**Context**: Asked to "review the changes" after having only written a design + plan for the S3
adapter in the visible session — no implementation had been done here.
**Symptom**: `git status` showed branch `feat/s3-document-storage-adapter` with six implementation
commits (`2c0a36b`…`74e41b6`) that were never produced in this conversation, plus two refinement
commits beyond the written plan.
**Root cause**: The plan had been executed in a separate session/agent; the working tree no longer
matched the last diff this context had produced.
**Fix**: Ran `git status -sb`, `git log --oneline main..HEAD`, and `git diff --stat main...HEAD` to
scope the actual review target before reading a single line.
**Rule**: Before reviewing "the changes", run `git status`/`git log`/`git diff --stat` to establish
the real review target — never assume the working tree matches the diff you last saw; in
multi-session/multi-agent work, commits land that your context never witnessed.

## Lesson 2: Review the diff against the approved spec, not only for intrinsic bugs

**Category**: pattern
**Context**: Reviewing the executed S3 adapter, whose design (approved earlier the same session)
explicitly said "credentials never in application properties — only the SDK default chain".
**Symptom**: No functional bug, but the implementation had added
`app.storage.s3.access-key-id`/`secret-access-key` static-credential properties — exactly the
pattern the design rejected.
**Root cause**: The implementer needed static creds to drive MinIO through Spring wiring in the IT
and reached for app properties; the deviation is invisible to a bug-only review.
**Fix**: Flagged it as the top (security-flavoured) finding and reworked the IT to feed creds via
the SDK default chain instead (see Lesson 3), removing the properties from production config.
**Rule**: Grade a diff against the approved design/spec, not just for crashes — a security-relevant
deviation from an agreed decision ("no secrets in app config") is a finding even when the code
works. Complements [[2026-07-13-ll-callback-secret-review]] (scope diffs to the task's baseline).

## Lesson 3: An IT can exercise production credential wiring without adding cred properties to config

**Category**: pattern
**Context**: The S3 adapter's config (`S3StorageConfiguration`) must use only
`DefaultCredentialsProvider` (no credential properties), yet the IT drives the full Spring wiring
against a MinIO container that needs credentials.
**Root cause**: MinIO needs an access key/secret, but production must not read them from app config.
**Fix**: The IT sets `aws.accessKeyId`/`aws.secretAccessKey` **system properties** in
`@DynamicPropertySource` (cleared in `@AfterAll`); the SDK's `SystemPropertyCredentialsProvider`
(part of the default chain) supplies them, so the very same `DefaultCredentialsProvider.create()`
used in production resolves the MinIO creds — no test-only credential branch in the bean.
**Rule**: To integration-test AWS SDK v2 wiring that must use only the default credential chain,
feed the test credentials to the chain via `aws.accessKeyId`/`aws.secretAccessKey` system
properties rather than adding credential properties to the production config or a static-provider
branch.

## Lesson 4: Default S3 path-style-access to false — native AWS needs virtual-hosted style

**Category**: gotcha
**Context**: Choosing the default for `app.storage.s3.path-style-access` on a generic
S3-compatible adapter.
**Root cause**: Path-style addressing (`endpoint/bucket/key`) is deprecated on native AWS S3, which
requires virtual-hosted style (`bucket.endpoint/key`) for many buckets/regions; MinIO/Ceph, by
contrast, require path-style. A default of `true` silently breaks native AWS.
**Fix**: Defaulted `path-style-access` to `false` (AWS-safe, matches the SDK's own default) and
documented that MinIO/Ceph deployments must set it `true`; the IT sets it `true` explicitly.
**Rule**: Default `path-style-access` to `false` for an S3 client and make MinIO/Ceph opt in with
`true` — the "S3-compatible generic" default must not break the most standard backend (native AWS).

## Lesson 5: JaCoCo 0.8.12 throws a non-fatal "major version 68" error on a Spring 7 class

**Category**: gotcha
**Context**: Running storage tests on JDK 25 with the project's JaCoCo 0.8.12 agent.
**Symptom**: A multi-line `ERROR`/stack trace — `java.io.IOException: Error while instrumenting
org/springframework/core/type/classreading/ClassFileMetadataReaderFactory with JaCoCo ...` →
`java.lang.IllegalArgumentException: Unsupported class file major version 68` — flooded the surefire
output and buried the `Tests run:` lines. The build was still `BUILD SUCCESS`.
**Root cause**: JaCoCo 0.8.12's bundled ASM can't instrument a Java-24-bytecode (major 68) class in
Spring Framework 7's multi-release JAR; the transformer logs the failure and loads the class
uninstrumented (coverage for that one class is silently dropped) — non-fatal.
**Fix**: Re-ran with `-Djacoco.skip=true` to get clean, readable test results while iterating.
**Rule**: A JaCoCo instrumentation `ERROR` on a single class is non-fatal (coverage skipped, build
still passes); when its stack trace hides your test results, re-run with `-Djacoco.skip=true` — and
don't mistake it for a test failure.

## Lesson 6: Run a single IT through the `verify` lifecycle, not the failsafe goal directly

**Category**: gotcha
**Context**: Trying to run just `S3DocumentStorageAdapterIT` quickly.
**Symptom**: `mvn failsafe:integration-test failsafe:verify -Dit.test=…` failed with a
`MojoExecutionException` before running anything — the IT class wasn't compiled.
**Root cause**: Invoking the failsafe goals directly skips the `test-compile` phase, so
`target/test-classes` has no compiled IT.
**Fix**: Ran through the lifecycle — `mvn -Dtest=S3DocumentStorageAdapterTest
-Dit.test=S3DocumentStorageAdapterIT verify` — which compiles first, and used `-Dit.test`/`-Dtest`
to restrict failsafe/surefire to the single IT + its unit test.
**Rule**: To run one IT, bind through the `verify` lifecycle (or run `test-compile` first) and
narrow with `-Dit.test=<IT>` (+ `-Dtest=<unit>`); calling `failsafe:integration-test` on its own
runs against stale/absent test-classes.
