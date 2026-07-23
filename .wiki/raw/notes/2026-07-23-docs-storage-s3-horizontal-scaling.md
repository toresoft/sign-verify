---
title: "Docs update: S3 storage backend, horizontal scaling page, MinIO in docker-compose"
type: documentation
source: "docs/{en,it}/01, 02, 08 + README.md / README.it.md (commit 4ffa04e, branch feat/s3-document-storage-adapter)"
date: 2026-07-23
tags: [documentation, s3-storage, horizontal-scaling, docker, minio, shedlock, kubernetes, hpa, configuration]
category: notes
confidence: high
summary: "The user-facing documentation now covers the S3 document-storage backend and horizontal scaling: config keys app.storage.type/app.storage.s3.* (IT+EN ch. 01), a new document-storage section with MinIO compose wiring (ch. 02), a brand-new horizontal-scaling chapter 08 (single vs multi-replica, atomic job claim, ShedLock singleton table, backpressure knobs, HPA metrics, filesystem→S3 drain runbook, scale-out checklist), and APP_STORAGE_* env vars in both READMEs."
---

# Docs update: S3 storage, horizontal scaling, MinIO (2026-07-23)

> Source material: commit `4ffa04e` *docs(storage): document S3 backend, horizontal scaling, MinIO
> docker case* on branch `feat/s3-document-storage-adapter` (10 files, +690/-16). This note captures
> what the documentation now asserts, so wiki articles can cite it.

## Configuration surface (docs/{en,it}/01 §"Storage and DSS")

| Key | Env | Default | Note |
|---|---|---|---|
| `storage.type` | `APP_STORAGE_TYPE` | `filesystem` | `filesystem` \| `s3` |
| `storage.jobs-dir` | `APP_STORAGE_JOBS_DIR` | `/var/lib/sign-verify/jobs` | filesystem only |
| `storage.s3.endpoint` | `APP_STORAGE_S3_ENDPOINT` | *(empty)* | empty = native AWS |
| `storage.s3.region` | `APP_STORAGE_S3_REGION` | `eu-south-1` | |
| `storage.s3.bucket` | `APP_STORAGE_S3_BUCKET` | `sign-verify-jobs` | must pre-exist (fail-fast `headBucket`) |
| `storage.s3.path-style-access` | `APP_STORAGE_S3_PATH_STYLE` | `false` | `true` for MinIO/Ceph |

- **No credential property exists.** The AWS SDK v2 client always resolves credentials through the
  **default chain** (env vars, IAM roles/IRSA, profiles), so no secret can land in committed config.
- Object keys are **relative** (`{jobId}/input-{safeName}`, `{jobId}/result.json`) and stored in the
  job's `document_path`; the filesystem backend stores **absolute** paths. The two never mix — a
  backend switch requires a drained queue, and there is no migration path.

## Docker (docs/{en,it}/02 §2.4, sections renumbered 2.5/2.6)

Three documented storage options: local `svdata` volume (single replica only), managed S3-compatible
object store (multi-replica), and **MinIO as a compose container** — explicitly scoped to local
development of the S3 path, CI/demo, on-prem/edge without a cloud object store, and air-gapped
installs; explicitly *not* recommended at production scale (prefer managed S3 or a real MinIO
cluster). Includes the `pgsty/minio` service snippet, the app-side `APP_STORAGE_*` + `AWS_*` env
wiring, and the note that the bucket must be created once out-of-band.

## New chapter 08 — Horizontal scaling (EN `08-horizontal-scaling.md`, IT `08-scalabilita-orizzontale.md`)

- **When to scale out**: sync semaphore (`verify.max-concurrent`, default 8) saturated, async queue
  growing despite backpressure, or fault tolerance required. Single replica is fine otherwise.
- **Writable paths are pod-local**: `jobs/` (unless S3) and `dss-cache/`. All shared state is in
  PostgreSQL, including the `shedlock` table.
- **Atomic job claim**: documented as an SQL conditional `UPDATE … WHERE id=:id AND status='PENDING'`
  — winner gets 1 row, losers 0; no distributed lock for workers.
- **ShedLock singleton duties table**: `tslRefresh` (cron `0 0 2 * * *`), `jobCleanup`
  (cron `0 30 3 * * *`), `callbackDispatch` (poll 10 s); lock expiry via `lockAtMostFor` lets another
  pod take over after a crash.
- **LB/probes**: `/actuator/health/liveness` vs `/actuator/health/readiness`; readiness includes
  `TslReadinessIndicator`, so a pod without a first TSL load is kept out of traffic. Graceful
  shutdown on SIGTERM.
- **Backpressure across replicas**: `verify.max-concurrent` and `async.workers` are **per pod**;
  `async.max-pending-per-principal` and `async.max-pending-global` stay **global** (DB-backed), so
  adding replicas multiplies throughput but not the queue ceiling.
- **HPA metrics**: `signverify.async.jobs.pending`, `signverify.async.jobs.running`,
  `signverify.storage.s3.operation` (Timer), `signverify.storage.s3.bytes` (DistributionSummary).
- **Honest limitation stated in the docs**: the DSS cache stays per-pod, so a fresh pod pays a
  warm-up penalty on its first verifications after a rollout/restart. Correctness does not depend on
  a shared cache — each replica can fetch TSL/CRL/OCSP independently.
- **Runbook filesystem → S3**: stop submissions → drain `PENDING`/`RUNNING` → set `APP_STORAGE_TYPE=s3`
  + `APP_STORAGE_S3_*` with the bucket provisioned → roll out. Old filesystem jobs are simply no
  longer referenced and expire via `jobCleanup`.
- **Scale-out prerequisites checklist**: S3 enabled, external PostgreSQL (not H2), LB on readiness,
  per-replica CPU/memory limits sized for DSS, and the **same `APP_SECRET_MASTER_KEY` on every pod**
  (env/secret, never locally generated).
- Two Mermaid diagrams: single-replica vs multi-replica deployment, and the atomic-claim sequence.

## READMEs

Both READMEs gained `APP_STORAGE_TYPE` and the four `APP_STORAGE_S3_*` rows plus a callout that S3
credentials come from the SDK default chain and that the bucket must exist (fail-fast `headBucket`).
