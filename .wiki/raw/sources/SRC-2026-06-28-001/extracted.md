# SiVa documentation collection — open-eid/SiVa

> **Source packet `SRC-2026-06-28-001`** — git collection ingest of the
> [open-eid/SiVa](https://github.com/open-eid/SiVa) documentation corpus.
> Rendered site: <https://open-eid.github.io/SiVa/>. License: **EUPL-1.1**.

## Provenance

| Field | Value |
|---|---|
| Upstream repo | https://github.com/open-eid/SiVa |
| Captured at commit | `348a6b261df9a16167eea4ef46593c442d2d6bc8` (HEAD, `--depth 1`) |
| Captured on | 2026-06-28 |
| Adapter | `git` (shallow clone, no HTML crawl) |
| File count | **60** text docs (890 KB) |
| License | EUPL-1.1 ([LICENSE.md](original/LICENSE.md)) |
| Full inventory | [`manifest.json`](manifest.json) — per-file `path`, blob `sha`, `bytes`, `canonical_url` |

**Scope included:** repository root `README.md`, `LICENSE.md`, `OSS_USED.md`, and the
entire `docs/` tree (`.md` + one `.txt`). Versions captured: SiVa **v1** (`docs/siva/`,
incl. `v2/` subfolder), **v2** (`docs/siva2/`), and **v3** (`docs/siva3/`).

**Excluded (not docs):** `.git/`, `.github/`, `validation-services-parent/**/test-files/`
(test vectors), binaries, images, vendored deps, scripts, build config.

## What SiVa is

SiVa (Signature Validation Service) is an Estonian / EU-co-funded REST web service that
validates eIDAS digital signatures via a JSON API. Supported inputs: Estonian DDOC/BDOC
containers, ASiC-S/ASiC-E, XAdes/CAdES/PAdES, and XAdES in hashcode form. It is built on
Spring Boot, DigiDoc4J (DDOC/BDOC), and the open-eid **DSS fork** (`sd-dss`) for everything
else. Directly relevant to [[entities/sign-verify-2]] and [[entities/siva]].

## File index

### Root

- [README.md](original/README.md) — project overview, build/run, requirements (JDK 17)
- [LICENSE.md](original/LICENSE.md) — EUPL-1.1 full text
- [OSS_USED.md](original/OSS_USED.md) — open-source dependencies list

### docs/ (top level)

- [docs/index.md](original/docs/index.md)
- [docs/version_info.md](original/docs/version_info.md)
- [docs/documentation_deployment_instructions.txt](original/docs/documentation_deployment_instructions.txt)

### docs/siva/ — SiVa v1 (+ v2 subfolder)

- [introduction.md](original/docs/siva/introduction.md) · [overview.md](original/docs/siva/overview.md)
- [definitions.md](original/docs/siva/definitions.md) · [component_diagram.md](original/docs/siva/component_diagram.md)
- [deployment_view.md](original/docs/siva/deployment_view.md) · [logging.md](original/docs/siva/logging.md)
- [qa_strategy.md](original/docs/siva/qa_strategy.md) · [test_plan.md](original/docs/siva/test_plan.md)
- [references.md](original/docs/siva/references.md) · [version_info.md](original/docs/siva/version_info.md)
- appendix: [known_issues.md](original/docs/siva/appendix/known_issues.md) ·
  [test_cases.md](original/docs/siva/appendix/test_cases.md) ·
  [validation_policy.md](original/docs/siva/appendix/validation_policy.md) ·
  [wsdl.md](original/docs/siva/appendix/wsdl.md)
- v2/: [deployment.md](original/docs/siva/v2/deployment.md) ·
  [interfaces.md](original/docs/siva/v2/interfaces.md) ·
  [links.md](original/docs/siva/v2/links.md) ·
  [siva_service_overview.md](original/docs/siva/v2/siva_service_overview.md) ·
  [structure_and_activities.md](original/docs/siva/v2/structure_and_activities.md) ·
  [systemintegrators_guide.md](original/docs/siva/v2/systemintegrators_guide.md) ·
  [use_cases.md](original/docs/siva/v2/use_cases.md)

### docs/siva2/ — SiVa v2

- [introduction.md](original/docs/siva2/introduction.md) · [overview.md](original/docs/siva2/overview.md)
- [definitions.md](original/docs/siva2/definitions.md) · [component_diagram.md](original/docs/siva2/component_diagram.md)
- [deployment.md](original/docs/siva2/deployment.md) · [deployment_view.md](original/docs/siva2/deployment_view.md)
- [interfaces.md](original/docs/siva2/interfaces.md) · [logging.md](original/docs/siva2/logging.md)
- [qa_strategy.md](original/docs/siva2/qa_strategy.md) · [test_plan.md](original/docs/siva2/test_plan.md)
- [references.md](original/docs/siva2/references.md) · [links.md](original/docs/siva2/links.md)
- [siva_service_overview.md](original/docs/siva2/siva_service_overview.md) ·
  [structure_and_activities.md](original/docs/siva2/structure_and_activities.md) ·
  [systemintegrators_guide.md](original/docs/siva2/systemintegrators_guide.md) ·
  [use_cases.md](original/docs/siva2/use_cases.md)
- appendix: [known_issues.md](original/docs/siva2/appendix/known_issues.md) ·
  [test_cases.md](original/docs/siva2/appendix/test_cases.md) ·
  [validation_policy.md](original/docs/siva2/appendix/validation_policy.md) ·
  [wsdl.md](original/docs/siva2/appendix/wsdl.md)

### docs/siva3/ — SiVa v3

- [background.md](original/docs/siva3/background.md) · [definitions.md](original/docs/siva3/definitions.md)
- [deployment.md](original/docs/siva3/deployment.md) · [deployment_guide.md](original/docs/siva3/deployment_guide.md)
- [interfaces.md](original/docs/siva3/interfaces.md) · [qa_strategy.md](original/docs/siva3/qa_strategy.md)
- [roadmap.md](original/docs/siva3/roadmap.md) ·
  [structure_and_activities.md](original/docs/siva3/structure_and_activities.md) ·
  [test_plan.md](original/docs/siva3/test_plan.md) ·
  [use_cases.md](original/docs/siva3/use_cases.md) ·
  [LOTL_update_April_2026.md](original/docs/siva3/LOTL_update_April_2026.md)
- appendix: [known_issues.md](original/docs/siva3/appendix/known_issues.md) ·
  [validation_policy.md](original/docs/siva3/appendix/validation_policy.md)

## Re-ingest / diff

This packet is immutable. To re-ingest, shallow-clone the new HEAD, run
`git ls-tree -r HEAD`, and compare each path's blob `sha` against `files[]` in
[`manifest.json`](manifest.json). Skip unchanged paths; write a new packet
(`SRC-<date>-NNN`) only if the revision moved. Do not overwrite this directory.

## Related wiki

- [[entities/siva]] — entity page for the SiVa service.
- [[analyses/siva-vs-sign-verify-2]] — comparison vs this project.
- [[sources/siva-research]] — prior research synthesis (2026-06-28); this raw packet is the
  primary-source backing for it and supersedes ad-hoc references.
