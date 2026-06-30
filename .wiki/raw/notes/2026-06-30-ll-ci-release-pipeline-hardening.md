---
title: "Lessons Learned: GitLab/GitHub CI release + vuln-scan pipeline hardening"
type: lessons-learned
source: session
date: 2026-06-30
tags: [lessons-learned, ci-cd, gitlab-ci, github-actions, dependency-check, trivy, caching]
lesson_count: 5
category: notes
confidence: high
summary: "Added tag-triggered releases (jar + vuln reports) to both .gitlab-ci.yml and .github/workflows/ci.yml; fixed a cache-quota-bloat bug and a cache-override bug found along the way; sped up dependency-check by disabling irrelevant analyzers."
---

# Lessons Learned: GitLab/GitHub CI release + vuln-scan pipeline hardening

> Extracted from session on 2026-06-30. 5 lessons covering GitHub Actions cache semantics, GitLab cache semantics, dependency-check tuning, and release-asset permanence.

## Lesson 1: GitHub Actions cache keyed by `run_id` never gets reused — silently fills the quota and evicts useful caches

**Category**: gotcha
**Context**: `.github/workflows/ci.yml` had `trivy-db-${{ github.run_id }}` and `dependency-check-nvd-${{ github.run_id }}` as cache keys, intended to persist the Trivy vulnerability DB and the OWASP NVD datastore across runs.
**Symptom**: No observed failure — this was caught by re-reading the workflow, not by a build break. The caches were technically "working" (restore-keys fell back to the latest prefix match) but every run still wrote a brand-new, never-reused cache entry.
**Root cause**: `actions/cache` keys are immutable — a `save` only succeeds if the exact key doesn't already exist. Since `github.run_id` is unique per run, the exact key never pre-exists, so every single run saves a new multi-hundred-MB entry. These accumulate until the repo's 10GB cache quota is hit, at which point GitHub evicts the *oldest* entries — which can include the actually-reused Maven cache (`setup-java`'s `cache: maven`), causing it to silently stop being effective.
**Fix**: Bucket the key by something stable-but-rotating instead of a per-run id, e.g. ISO week (`date -u +%G-W%V`). Same-week runs hit the cache; the DB still refreshes weekly.
**Rule**: Never put a per-run-unique value (`run_id`, `run_number`, a timestamp) in an `actions/cache` key when the goal is reuse across runs — pick a value that's stable for the reuse window you want (a date bucket, a content hash) and rely on `restore-keys` only as the fallback, not the primary hit path.

## Lesson 2: A GitLab job-level `cache:` block replaces the global `cache:`, it does not merge with it

**Category**: gotcha
**Context**: `.gitlab-ci.yml` has a global `cache:` block keyed on `pom.xml` for the shared Maven local repo. `security:dependency-scan` additionally declared its own job-level `cache: { key: dependency-check-nvd-data, paths: [.dependency-check-data/] }` to persist the OWASP NVD datastore.
**Symptom**: No visible failure (job still passed) — caught only by re-reading the pipeline for the "speed up vulnerability checks" ask. The job-level cache silently won, so the global Maven cache was never restored in this job: every run re-resolved every Maven dependency and re-downloaded the `dependency-check-maven` plugin itself from scratch.
**Root cause**: GitLab's `cache:` keyword at job level fully overrides the global default `cache:`, it does not append to it. A single `cache:` map per job means "use only this."
**Fix**: Use the array form of `cache:` to declare multiple cache configs in one job — re-list the Maven cache (same key/prefix as the global one, `policy: pull` since this job doesn't need to write it back) alongside the NVD cache.
**Rule**: Any GitLab job that defines its own `cache:` loses the global cache unless it explicitly re-declares it (as an array entry, typically with `policy: pull` if the job is a consumer, not the owner, of that cache).

## Lesson 3: OWASP dependency-check runs analyzers for ecosystems the project doesn't use, by default

**Category**: discovery
**Context**: `org.owasp:dependency-check-maven:check` was being invoked with no analyzer-disabling flags, on a project with zero Maven plugin config for dependency-check (i.e. all defaults active).
**Root cause**: dependency-check ships analyzers for Node (npm/yarn audit), Python, Ruby, Cocoapods, Swift, Go, and .NET assemblies, all enabled by default regardless of what the project actually is. Several of these make their own outbound network calls (npm audit API, retire.js CVE feed, Sonatype OSS Index) on every run, adding latency (and, for the anonymous OSS Index endpoint, rate-limit risk) for ecosystems that don't exist in a pure-Java/Maven repo.
**Fix**: Pass `-D<analyzer>Enabled=false` for the irrelevant ones (`assemblyAnalyzerEnabled`, `nodeAnalyzerEnabled`, `nodeAuditAnalyzerEnabled`, `retireJsAnalyzerEnabled`, `ossindexAnalyzerEnabled`, `pyDistributionAnalyzerEnabled`, `pyPackageAnalyzerEnabled`, `rubygemsAnalyzerEnabled`, `cocoapodsAnalyzerEnabled`, `swiftPackageManagerAnalyzerEnabled`, `golangDepEnabled`, `golangModEnabled`).
**Rule**: When running dependency-check-maven on a single-ecosystem project, explicitly disable analyzers for every ecosystem the project doesn't contain — the defaults assume a polyglot repo and pay for it in scan time and network calls.

## Lesson 4: GitLab release assets pointing at job-artifact URLs go dead after the artifact's `expire_in`

**Category**: pattern
**Context**: Adding a `release` stage that attaches the built jar and the vulnerability reports (dependency-check HTML, Trivy txt) to the GitLab Release for a tag.
**Root cause**: The jar and reports are produced as job artifacts with `expire_in: 1 week`. A release link built from `${CI_PROJECT_URL}/-/jobs/<job_id>/artifacts/raw/<path>` would 404 once that job's artifacts expire — releases are meant to be permanent, artifacts are not.
**Fix**: Added a `release:upload` job that pushes the jar and both reports to the project's generic package registry (`curl --header "JOB-TOKEN: $CI_JOB_TOKEN" --upload-file ... "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/generic/<pkg>/<version>/<file>"`) before `release:create` runs; the release's asset links point at the generic-package URLs, which don't expire.
**Rule**: Never link a GitLab Release asset directly at a job-artifact URL — job artifacts have a finite `expire_in` and the link will eventually break. Stage anything release-bound through the generic package registry (or another permanent store) first.

## Lesson 5: Trivy's default `table` format only prints to stdout — no file exists to attach as an artifact unless `output`/`--output` is set

**Category**: gotcha
**Context**: Both `security:image-scan` (GitLab) and the `trivy` job (GitHub Actions) ran `trivy image --severity HIGH,CRITICAL ...` with table output and no explicit output file, then (for the new release work) needed to attach the scan result as a release asset.
**Root cause**: `format: table` (the existing default in both pipelines) writes only to the job log; there was never a file on disk to upload as an artifact or attach to a release.
**Fix**: Added `--output trivy-report.txt` (GitLab CLI flag) / `output: trivy-report.txt` (`aquasecurity/trivy-action` input) so the same table-formatted result also lands in a file, then uploaded/pushed that file as an artifact.
**Rule**: Before wiring a scanner's output into an artifact or release pipeline, confirm the tool is actually writing a file — many CLI scanners (Trivy included) default to stdout-only and need an explicit output flag.
