---
type: concept
domain: engineering
created: 2026-07-23
updated: 2026-07-23
sources:
  - notes/2026-07-22-ll-cve-bumps-scaling-s3-adapter
  - notes/2026-07-22-ll-s3-adapter-review-and-fixes
  - notes/2026-07-23-docs-storage-s3-horizontal-scaling
volatility: warm
---

# Document storage backends (filesystem vs S3)

The two implementations of `DocumentStoragePort` in [[entities/sign-verify-2]] — the port that holds
the uploaded document and the `result.json` of an async job ([[concepts/async-verification-jobs]]).
Selection is a single config key; the choice decides whether the service can run more than one
replica.

| | `filesystem` (default) | `s3` |
|---|---|---|
| Adapter | `FilesystemDocumentStorageAdapter` | `S3DocumentStorageAdapter` (AWS SDK v2) |
| Path stored in `document_path` | **absolute**, pod-local | **relative key** in the bucket |
| Visible to other replicas | no | yes |
| Config | `app.storage.jobs-dir` | `app.storage.s3.*` |

Wired via `@ConditionalOnProperty` on `app.storage.type` (`filesystem` \| `s3`), so exactly one bean
exists; `StorageKeys` is shared so both backends produce identical leaf names
(`{jobId}/input-{safeName}`, `{jobId}/result.json`).

## Configuration

| Key | Env | Default |
|---|---|---|
| `storage.type` | `APP_STORAGE_TYPE` | `filesystem` |
| `storage.s3.endpoint` | `APP_STORAGE_S3_ENDPOINT` | *(empty = native AWS)* |
| `storage.s3.region` | `APP_STORAGE_S3_REGION` | `eu-south-1` |
| `storage.s3.bucket` | `APP_STORAGE_S3_BUCKET` | `sign-verify-jobs` |
| `storage.s3.path-style-access` | `APP_STORAGE_S3_PATH_STYLE` | `false` |

- **There is deliberately no credential property.** `S3StorageConfiguration` builds the client from
  the **SDK v2 default credentials chain** only (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, IAM
  roles/IRSA, profiles), so no secret can land in committed configuration. The integration test
  reaches the same chain through the `aws.accessKeyId`/`aws.secretAccessKey` system properties.
- `path-style-access` defaults **false** because native AWS wants virtual-hosted addressing; MinIO
  and Ceph must opt in with `true`.
- The bucket must pre-exist: the adapter does a **fail-fast `headBucket`** at startup and the service
  refuses to boot if the bucket is missing or unreachable.
- Metrics: `signverify.storage.s3.operation` (Timer), `signverify.storage.s3.bytes`
  (DistributionSummary) — usable as HPA signals alongside queue depth.
- Delete is **best-effort**: `SdkException` on delete is swallowed rather than failing the caller.

## Switching backends is a drain, not a migration

Absolute paths and relative keys never cross, and no migration tooling exists. Documented runbook:
stop accepting new jobs → wait for all `PENDING`/`RUNNING` to reach a terminal state → set
`APP_STORAGE_TYPE=s3` and the `APP_STORAGE_S3_*` vars with the bucket provisioned → roll out. Jobs
written under the old backend are simply no longer referenced and disappear with the `jobCleanup`
retention sweep ([[concepts/shedlock]]). This is acceptable only because job retention is short.

## Why it matters

Local filesystem storage was the **single remaining blocker to horizontal scaling**: a payload
written by instance A is invisible to instance B that wins the DB-CAS claim. Everything else in the
pipeline was already multi-replica safe — see the scaling section of
[[concepts/async-verification-jobs]]. MinIO in docker-compose is documented for local development of
the S3 path, CI/demo, on-prem/edge and air-gapped installs — explicitly **not** for production
scale, where a managed S3 or a real MinIO cluster is expected.

The port/adapter split is what made this a config switch rather than a refactor — see
[[concepts/hexagonal-architecture]].

## Related
- [[entities/sign-verify-2]] · [[concepts/async-verification-jobs]] · [[concepts/hexagonal-architecture]]
- [[concepts/shedlock]] · [[concepts/file-extraction]]
- Docs: `docs/{en,it}/01` §Storage and DSS, `docs/{en,it}/02` §2.4, `docs/{en,it}/08` (horizontal scaling)
