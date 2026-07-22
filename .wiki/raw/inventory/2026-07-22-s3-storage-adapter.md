---
title: "Inventory: S3 storage adapter (horizontal-scaling unblock)"
type: inventory
source: session
date: 2026-07-22
tags: [inventory, follow-up, horizontal-scaling, s3-storage, testcontainers, jacoco, build-tooling]
category: inventory
confidence: high
summary: "S3-compatible DocumentStoragePort adapter that unblocks horizontal scaling — implemented on branch feat/s3-document-storage-adapter (with review fixes), full verify green, pushed to both mirror remotes, not merged (no PR). Plus a JaCoCo-on-JDK25 tooling watch."
---

# Inventory: S3 storage adapter

> Tracked items with status / priority / next action. Raw lessons in [[2026-07-22-ll-cve-bumps-scaling-s3-adapter]] and [[2026-07-22-ll-s3-adapter-review-and-fixes]].

## INV-5: S3 `DocumentStoragePort` adapter (unblocks horizontal scaling)

- **Type**: feature / scaling enabler
- **Status**: pushed — branch `feat/s3-document-storage-adapter` (6 impl commits + review-fix `a242a2f`) reviewed, all 5 findings resolved; full `mvn verify` green (579 unit + 27 IT incl. the MinIO IT, Spotless clean); pushed to BOTH mirror remotes (`origin` GitHub, `origin-` GitLab). NOT merged (user opted for no PR/MR).
- **Priority**: high (single remaining blocker to running N replicas; rest of the pipeline is already multi-replica safe)
- **Origin**: 2026-07-22 scaling analysis + brainstorm/design/plan, then implementation + review/fix + verify/push.
- **Detail**: `S3DocumentStorageAdapter implements DocumentStoragePort` (AWS SDK v2), selected via `app.storage.type=filesystem|s3` (`@ConditionalOnProperty`, filesystem default). Keys `{jobId}/input-{safeName}` / `{jobId}/result.json` in the DB. Fail-fast `headBucket`, metrics (`signverify.storage.s3.operation` Timer, `signverify.storage.s3.bytes` DistributionSummary), no migration (drain switch). `S3StorageConfiguration` (in `config/`) builds the client from the **SDK default chain only** (no cred properties); `path-style-access` defaults false (native AWS), MinIO/Ceph set true. Shared `StorageKeys` keeps filesystem/S3 leaf names identical. IT against `pgsty/minio`.
- **Next action**: decide the merge into `main` (user deferred — no PR opened); on merge, tag + align `develop` on both remotes, then provision a bucket + credentials (IAM/IRSA or env) and add an HPA on `signverify.async.jobs.pending`.
- **Refs**: spec `docs/superpowers/specs/2026-07-22-s3-document-storage-adapter-design.md`; plan `docs/superpowers/plans/2026-07-22-s3-document-storage-adapter.md` (both gitignored — local only); `adapter/storage/{S3DocumentStorageAdapter,StorageKeys,FilesystemDocumentStorageAdapter}.java`, `config/S3StorageConfiguration.java`.

## INV-6: JaCoCo 0.8.12 cannot instrument a Spring 7 multi-release class on JDK 25

- **Type**: build tooling / watch item
- **Status**: monitoring (non-fatal; coverage silently dropped for one class, build stays green)
- **Priority**: low
- **Origin**: surfaced while verifying the S3 adapter (see [[2026-07-22-ll-s3-adapter-review-and-fixes]] L5).
- **Detail**: JaCoCo 0.8.12 throws `Unsupported class file major version 68` instrumenting `org/springframework/core/type/classreading/ClassFileMetadataReaderFactory` (Java-24 bytecode in Spring Framework 7's multi-release JAR). It logs a full stack trace on every context-loading test run and buries `Tests run:` lines; coverage for that class is skipped.
- **Next action**: bump JaCoCo when a release supporting major-68 bytecode ships (check `jacoco.version` in `pom.xml`, currently 0.8.12); meanwhile use `-Djacoco.skip=true` when iterating on test output.
- **Refs**: `pom.xml` (`<jacoco.version>`).
