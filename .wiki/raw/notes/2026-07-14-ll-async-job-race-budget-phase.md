---
title: "Lessons Learned: async job optimistic-lock race + DSS timeouts / job budget / phase observability"
type: notes
source: session
ingested: 2026-07-14
tags: [lessons-learned, async-jobs, optimistic-locking, dss, observability, code-review, diagnosis]
lesson_kind: lessons-learned
lesson_count: 6
category: notes
confidence: high
summary: "A 'stuck async job' the user blamed on revocation lists was actually an optimistic-lock race: the GET /jobs/{id} last-access stamp (setLastAccessedAt + save) bumped @Version and made the worker's final repo.save() throw StaleObjectStateException, dropping the result. Fix = version-neutral bulk touch + conditional bulk finalize keyed on status=RUNNING. Follow-on hardening (DSS timeouts, per-job budget, phase observability) added its own lessons: enforce wall-clock budgets with a per-instance in-memory deadline map (a DB sweep can't tell slow-alive from crashed across instances); a gate checked at phase ENTRY can never protect a phase that accrues its duration after entry; a conditional finalize that no-ops orphans any side effect it wrote first; DSS OnlineOCSP/CRL/AIA sources are unbounded unless you inject a timeout-configured CommonsDataLoader; and kill -3 1 in the container dumps threads to stdout."
---

# Lessons Learned: async job optimistic-lock race + timeouts/budget/phase observability

> Extracted from a session on 2026-07-14 diagnosing a real "stuck job with 5 signatures" in
> [[entities/sign-verify-2]], then designing/reviewing DSS request timeouts, a per-job time
> budget, and phase observability. 6 lessons: a diagnosis technique, the root-cause race and its
> fix, and four design/review lessons from the hardening work. Builds on
> [[2026-07-03-ll-code-review-fixes-hardening]] (bulk JPQL bypasses @Version; self-invocation
> @Transactional → stale-RUNNING sweep).

## Lesson 1: A live JVM thread dump from the container disproves the "obvious" hypothesis before you touch code

**Category**: gotcha
**Context**: A user reported an async validation job (a PDF with 5 signatures) "still running" and supposed it was stuck fetching revocation lists (OCSP/CRL). Before accepting that, gather evidence.
**Symptom**: Job in RUNNING for ~25 min; user's guess was "stuck on revocation lists".
**Root cause**: The guess was wrong — the worker threads were parked idle in the executor, and the real failure was an exception buried in the JSON logs (see Lesson 2). Revocation calls were slow but progressing.
**Fix**: `podman exec <container> kill -3 1` makes the JVM print a full thread dump to **stdout**, captured via `podman logs`. That, plus grepping the container logs for the job id and inspecting the `validation_job` DB row (`status`, `version`, `started_at`), located the actual failure. The revocation-list hypothesis was disproved in minutes.
**Rule**: For a "stuck" JVM in a container, dump threads with `kill -3 1` (→ stdout → `podman/docker logs`) and read the logs + DB state before writing any fix; the user's causal guess is a hypothesis to test, not a starting assumption.

## Lesson 2: A metadata "touch" that does `entity.setX(); save()` bumps `@Version` and destroys a long-running worker's outcome write

**Category**: pattern
**Context**: An async worker claims a job, loads the `ValidationJob` entity, runs a minutes-long DSS validation, then persists the outcome with `repo.save(job)`. Meanwhile `GET /jobs/{id}` did `job.setLastAccessedAt(now); repo.save(job)` to stamp last access.
**Symptom**: `ObjectOptimisticLockingFailureException` / `StaleObjectStateException` on the worker's final `save`; the completed result was silently dropped and the job sat RUNNING until the 30-min stale-RUNNING sweep. The DB row was at `version=54` — one bump per client poll.
**Root cause**: Each polling GET bumped the `@Version` column. The worker held a copy loaded at claim time, so any poll during the (slow) validation made its copy stale. The more a client polled a slow job, the more likely its result was thrown away. The `catch` branch then tried to write FAILED on the *same* stale entity and failed identically, so the exception propagated.
**Fix**: Route the last-access stamp through a **version-neutral bulk UPDATE** (`touchLastAccessed`, same idiom as `ApiKeyRepository.touchLastUsed`) instead of `setX + save`. Route the worker's outcome writes through **conditional bulk UPDATEs keyed on `status = RUNNING`** (`markCompleted`/`markFailed`/`requeueTransient`, explicit `version = version + 1`), never `save()` of the long-held entity. The worker reads the job read-only and finalizes via those; a job requeued/expired mid-validation yields 0 rows → logged no-op, not an exception.
**Rule**: Metadata stamps (last-accessed, phase) must be version-neutral bulk updates; a worker's outcome write after a long operation must be a conditional bulk update on the state it expects, never `save()` of an entity loaded much earlier — otherwise any concurrent row write (even a harmless metadata touch) makes the outcome a lost update. Builds on [[2026-07-03-ll-code-review-fixes-hardening]] (bulk JPQL bypasses `@Version`).

## Lesson 3: Enforce a wall-clock job budget with a per-instance in-memory deadline map, not a DB sweep

**Category**: pattern
**Context**: Adding a hard per-job time budget so a slow validation fails deterministically with `job.timeout` instead of hanging until the 30-min stale sweep. Deployment is multi-instance.
**Symptom** (design flaw avoided): The obvious implementation — a scheduled `UPDATE ... WHERE status=RUNNING AND started_at < cutoff` — would mark over-budget jobs FAILED across the whole cluster.
**Root cause**: A DB sweep cannot distinguish "slow but still alive on instance B" from "crashed on instance B"; both have an old `started_at`. It would kill jobs another instance is actively validating.
**Fix**: Each instance keeps an in-memory `ConcurrentHashMap<UUID, Instant> deadlines` for the jobs *it* is running (put on submit, remove in the worker's `finally`). A short `@Scheduled` watchdog times out only its own past-deadline jobs via a conditional `markTimedOut(id)` (status=RUNNING). The longer DB stale-RUNNING sweep stays, but only for crash recovery (a dead instance loses its in-memory deadlines → its jobs get requeued after the long cutoff, not falsely timed out). Constraint enforced at construction: `max-validation-time < stale-running-timeout`.
**Rule**: Liveness/timeout decisions that require "is this owner still working?" belong to the owning instance (in-memory), not to a shared DB predicate that can't see per-instance liveness; use the DB sweep only as the longer crash-recovery backstop.

## Lesson 4: A gate evaluated at phase ENTRY can never protect a phase that accrues its duration AFTER entry

**Category**: gotcha
**Context**: Phase observability stamps `phase` (STORAGE_READ → POLICY_RESOLVE → DSS_VALIDATE → RESULT_STORE) so a stuck job is diagnosable. To avoid a DB write per fast job, a reviewer-suggested optimization gated the stamp: `if (elapsed-since-claim < 1s) skip setPhase`.
**Symptom**: A job hung for minutes inside DSS_VALIDATE showed `phase = null` — exactly the stuck job the feature exists to locate had no phase recorded.
**Root cause**: The gate was checked at phase *entry*. STORAGE_READ and POLICY_RESOLVE are sub-second, so at DSS_VALIDATE entry the elapsed time was still < 1s → the stamp was skipped, and then validation hung for minutes. A phase accrues its duration *after* it is entered, so entry-time elapsed can never predict whether the phase will be slow. Worse, the unit test masked this by reaching into the threshold seam (`setPhaseStampThresholdForTest(0)`) so all phases stamped.
**Fix**: Drop the gate; stamp unconditionally. The write is one small conditional UPDATE on a semaphore-gated (few-concurrent) path, so the "amplification" it optimized away was negligible. Remove the wall-clock-dependent test.
**Rule**: Never gate an observability write for a step by cumulative-time-*at-entry* — it blinds you to the step that hangs. And treat a test that pokes a threshold/seam to make its assertion pass as a smell: it can hide the exact production defect the assertion appears to cover.

## Lesson 5: A conditional finalize that no-ops orphans any side effect it wrote first

**Category**: gotcha
**Context**: The worker writes the result blob (`storage.storeResult(jobId, ...)` → `<jobId>/result.json`) and then finalizes with `markCompleted(id, resultPath)` (conditional on status=RUNNING). When the budget watchdog (or stale sweep / expiry) already flipped the job out of RUNNING, `markCompleted` returns 0 and `result_path` stays null.
**Symptom**: Every timed-out job left a `result.json` blob on disk forever. The cleanup scheduler deletes results via `storage.delete(job.getResultPath())` = `delete(null)` — a no-op — so nothing ever reclaims the orphan.
**Root cause**: The side effect (blob write) happens *before* the conditional finalize learns it lost the race; on the no-op branch the blob is written but never referenced.
**Fix**: On `markCompleted == 0`, `storage.delete(resultPath)` to reclaim the just-written file (best-effort, delete-if-exists).
**Rule**: When a conditional finalize can no-op, any side effect performed before it (blob write, external call) must be explicitly rolled back on the 0-rows branch — the DB row won't reference it, so no downstream cleanup keyed on that reference will ever find it.

## Lesson 6: DSS `OnlineOCSPSource` / `OnlineCRLSource` / `DefaultAIASource` are unbounded unless you inject a timeout-configured `CommonsDataLoader`

**Category**: discovery
**Context**: Bounding revocation/AIA network latency during validation. The `CertificateVerifier` was built with `new OnlineOCSPSource()` / `new OnlineCRLSource()` / `new DefaultAIASource()` (no-arg).
**Symptom**: Revocation fetches to dead endpoints (e.g. `ocsp.nbu.gov.sk`) consumed long connect timeouts serially, per certificate, per signature — minutes of wall-clock for a 5-signature document.
**Root cause**: Each no-arg source instantiates its **own default** `CommonsDataLoader`; nothing in our config bounded them, and they are separate from the TSL `FileCacheDataLoader`.
**Fix**: Build a `CommonsDataLoader` configured with (DSS 6.4 API) `setTimeoutConnection` / `setTimeoutConnectionRequest` / `setTimeoutResponse` / `setTimeoutSocket` (int millis) and inject the same instance into all three revocation/AIA sources via their `DataLoader` constructor; apply the same timeouts to the TSL loader too. Semantics unchanged: an unreachable revocation still degrades to INDETERMINATE (correct eIDAS behavior), it just fails fast.
**Rule**: In DSS, per-call HTTP timeouts are a property of the injected `DataLoader`, not a global setting — the online OCSP/CRL/AIA sources each build an unbounded default loader unless you construct them with a timeout-configured `CommonsDataLoader`. See [[entities/dss]].
