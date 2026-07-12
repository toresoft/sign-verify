---
title: "Lessons Learned: Docker image entrypoint, healthcheck, and HTTP e2e in CI"
type: notes
source: session
ingested: 2026-07-12
tags: [lessons-learned, docker, spring-boot4, github-actions, healthcheck, e2e, podman]
lesson_kind: lessons-learned
lesson_count: 6
category: notes
confidence: high
summary: "Boot 4.1 jarmode=tools extract --layers yields a slim jar named after finalName (not application.jar / not JarLauncher); OCI/podman silently drops HEALTHCHECK; only booting the real image over HTTP catches ENTRYPOINT/startup bugs; and event_name=='push' is not a tag guard."
---

# Lessons Learned: Docker image entrypoint, healthcheck, and HTTP e2e in CI

> Extracted from session on 2026-07-12. 6 lessons covering the Boot 4.1 layered-jar
> extraction layout, an OCI/podman healthcheck gotcha, a trust-independent Docker
> e2e added to CI, and a GitHub Actions trigger/publish-guard pitfall.
> Related prior note: [[2026-06-30-ll-ci-release-pipeline-hardening]].

## Lesson 1: `jarmode=tools extract --layers` (Boot 4.1) produces a slim jar named after finalName, not exploded layers

**Category**: gotcha
**Context**: The Dockerfile ran `java -Djarmode=tools -jar target/sign-verify-2.jar extract --layers --destination target/extracted`, copied `extracted/{dependencies,spring-boot-loader,snapshot-dependencies,application}/` into `/app`, and had `ENTRYPOINT ["java", "-jar", "application.jar"]`.
**Symptom**: Container failed with `Unable to access jarfile application.jar`. There is no `application.jar` in `/app` — nor any exploded `BOOT-INF/` tree.
**Root cause**: In Spring Boot 4.1, the tools `extract` deposits the `application` layer as a **slim runnable jar named after the Maven `finalName`** (`sign-verify-2.jar`) plus a `lib/` dir; the jar's manifest has `Main-Class: org.toresoft.signverify.SignVerifyApplication` directly and `Class-Path: lib/...`. It is NOT the classic exploded layout, so neither `application.jar` nor `org.springframework.boot.loader.launch.JarLauncher` is the right entrypoint here.
**Fix**: `ENTRYPOINT ["java", "-jar", "sign-verify-2.jar"]` (name tracks `finalName`; `java -jar` resolves `lib/` via the manifest Class-Path).
**Rule**: For a Boot 4.x tools-`extract` image, the launch target is the slim `<finalName>.jar` with its Class-Path manifest — verify the actual `/app` contents and `MANIFEST.MF` before choosing `java -jar <jar>` vs `JarLauncher`; they are mutually exclusive layouts.

## Lesson 2: A stale local `target/extracted` sent the diagnosis the wrong way — inspect the built image, not local artifacts

**Category**: correction
**Context**: Diagnosing the entrypoint bug, the first fix was `JarLauncher`, based on reading the local `target/extracted/` which showed the classic exploded layout (`application/BOOT-INF/classes`, `spring-boot-loader/org/...`).
**Symptom**: The `JarLauncher` entrypoint then failed inside the container with `ClassNotFoundException: org.springframework.boot.loader.launch.JarLauncher` — because `/app` actually held `sign-verify-2.jar` + `lib/`, not the loader classes.
**Root cause**: The local `target/extracted/` was a stale leftover from a previous/different extraction run; the Docker build does its OWN `extract` inside the build stage, whose output (slim jar + lib) never matched the local dir.
**Fix**: Inspected the real image with `docker run --rm --entrypoint sh <img> -c 'pwd; ls /app; ...'` and read the in-image `MANIFEST.MF` — that revealed the slim-jar layout and the correct entrypoint.
**Rule**: When a container behaves unlike your local build tree, treat the built image as the source of truth (`docker run --entrypoint sh ... ls` / read the in-image manifest); do not reason from local `target/` artifacts that a multi-stage build regenerates internally.

## Lesson 3: OCI-format images (podman default) silently drop the Dockerfile `HEALTHCHECK` — declare it at the compose level

**Category**: gotcha
**Context**: Building the image locally with podman (which emulates the `docker` CLI here).
**Symptom**: Build log warning `HEALTHCHECK is not supported for OCI image format and will be ignored. Must use \`docker\` format`; the container never reported health status locally.
**Root cause**: podman builds OCI-format images by default, and the OCI image spec has no healthcheck field, so the Dockerfile `HEALTHCHECK` is dropped. GitHub CI builds with real Docker/BuildKit, so it works there — the gap is podman-local only.
**Fix**: Added a compose-level `healthcheck:` to the dev `docker-compose.yml` `app` service (the prod compose already had one). Compose applies healthchecks at runtime regardless of image format, so it works under both docker and podman. (`--format docker` at build time is the alternative.)
**Rule**: Don't rely solely on the Dockerfile `HEALTHCHECK` for local/compose orchestration — declare it at the compose level too, because podman/OCI images drop the image-embedded healthcheck. (In Kubernetes the image healthcheck is ignored anyway; probes live in the manifests.)

## Lesson 4: Only booting the real image over HTTP catches ENTRYPOINT/startup/wiring regressions that unit + integration tests miss

**Category**: pattern
**Context**: The `application.jar` entrypoint bug shipped despite a green `mvn verify` (unit + Testcontainers IT) and a successful image build — none of them run the container.
**Symptom**: A broken container that starts to nothing, undetected by the test suite and even by `docker build` success.
**Root cause**: JVM/Spring tests exercise the code, not the packaged-and-launched artifact; `docker build` proves the image assembles, not that its entrypoint launches the app.
**Fix**: Added a CI `e2e` job (`scripts/docker-e2e-test.sh`) that boots the built image + PostgreSQL on a user-defined docker network, waits for `/actuator/health/readiness == UP`, and drives a real `POST /api/v1/verifications`. Gated it behind compile + normal tests (`needs: build`) and made image publish (`package`) depend on it.
**Rule**: For any service shipped as a container, add a pipeline stage that boots the actual image and drives one real request end-to-end; gate publishing on it, because build-success and code-level tests cannot catch a broken launch.

## Lesson 5: Make the container e2e trust-independent — the `docker` profile skips TSL, so assert a well-formed report, not TOTAL_PASSED

**Category**: discovery
**Context**: Writing assertions for the verification e2e without network access to the EU LOTL/TSL.
**Symptom/quirk**: With `app.tsl.refresh.startup-mode: SKIP` (the `docker` profile) a valid CAdES-BES fixture returns **HTTP 200** with `indication: INDETERMINATE` / `subIndication: NO_CERTIFICATE_CHAIN_FOUND` (no trust anchors) — not a failure. Auth uses header `X-API-Key`; the app writes a one-time bootstrap key to `/var/lib/sign-verify/bootstrap-api-key.txt` (mode 0600, owned by uid 10001) on first boot, readable via `docker exec <app> cat ...` since exec runs as the same user.
**Root cause**: Trust outcome depends on external TSLs; a hermetic CI run has none, so cryptographic-validity indication is intentionally INDETERMINATE.
**Fix**: Assert structural facts only — HTTP 200, parseable JSON report, `signatureCount >= 1`, non-empty `indication` — never `TOTAL_PASSED`. Generate `APP_SECRET_MASTER_KEY` per run; read the bootstrap key from inside the container. (Matches the calibrated no-TSL behaviour in [[siva-corpus-tests]].)
**Rule**: Container/integration signature-verification checks must be trust-independent: assert the report is well-formed (200 + detected signature + present indication), because without TSLs the correct outcome is INDETERMINATE, not TOTAL_PASSED.

## Lesson 6: `github.event_name == 'push'` matches branch AND tag pushes — never use it as a "release" guard

**Category**: gotcha
**Context**: Broadening `ci.yml` triggers to also run on `push`/`pull_request` to `main`/`develop`, while the image-publish job (`package`) was guarded by `if: github.event_name == 'push' || github.event_name == 'workflow_dispatch'`.
**Symptom**: With the broadened triggers, that guard would make `package` publish `:latest`/`:sha` to Docker Hub on **every branch push**, because a branch push is also `event_name == 'push'`.
**Root cause**: `event_name` does not distinguish a tag push from a branch push; only the ref does.
**Fix**: Guard publish/scan jobs with `if: startsWith(github.ref, 'refs/tags/') || github.event_name == 'workflow_dispatch'`, and made `package` depend on `e2e`. Branch pushes/PRs now run `validate → test → build → e2e` without publishing.
**Rule**: To gate a job to release tags in GitHub Actions use `startsWith(github.ref, 'refs/tags/')`, never `github.event_name == 'push'` — the latter is true for branch pushes too. When widening a workflow's triggers, re-audit every job's `if:` for publish/side-effect steps.

