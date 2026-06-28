# Audit Report — 2026-06-28

**Scope:** `wiki/analyses/siva-vs-sign-verify-2` + dependency chain (artifact audit)
**Flags:** `--artifact` (implicit, derived from session context)
**Provenance:** `.session-events.jsonl` present (replayable), no interrupted research sessions

## Trust Verdict

The analysis `analyses/siva-vs-sign-verify-2` is **partially accurate** after corrections applied during this session (4 table rows corrected, 2 backlog points verified as done, verification footer added). The comparison table is now grounded in raw SiVa docs (`SRC-2026-06-28-001`). **High confidence** on SiVa side (primary-source docs), **medium-high** on sign-verify-2 side (code-level verification performed).

However, the referenced output artifact `outputs/improvement-points-from-siva-2026-06-28.md` is **severely drifted** — its body predates commits `dd9878f`/`4b4c262`/`6912fc5` and contradicts the corrected wiki analysis on 4 of 9 points.

## Backlog Audit — 9 points verified against code

| # | Point | Code state | Verdict |
|---|---|---|---|
| 1 | Report arricchito | `signedBy`, `bestSignatureTime`, `signatureLevel`, `signatures[].timestamps[]` (mapper `:20-35`). Missing: `claimedSigningTime`, `archiveTimeStamps[]` separate, `certificates[]` typed. | **PARTIAL** |
| 2 | signatureLevel | `SignatureSummary.signatureLevel` ← `getSignatureQualification(id)` (commit `dd9878f`). Tested. | ✅ **DONE** |
| 3 | Signed report | No `ReportSignerPort`, no PKCS#11/12 adapter. 0 files. | ❌ NOT DONE |
| 4 | Hashcode validation | No `/validateHashcode` endpoint, no `DigestDocument` usage. 0 files. | ❌ NOT DONE |
| 5 | Health indicators | `TslReadinessIndicator` + `DssHealthIndicator` + `JobQueueHealthIndicator` + `/actuator/info` (commit `4b4c262`). 4/4 tests green. | ✅ **DONE** |
| 6 | Corpus + perf | No Gatling/k6. Small test corpus in `src/test/resources/assets/`. No system perf test. | ❌ NOT DONE |
| 7 | Cablare audit | `TslRefreshScheduler` (6×), `AsyncVerificationController` (1×), `TslController` (1×) call `audit.log`. `VerificationController` (sync) and `ValidationWorker` do NOT. Entity page still says "not yet wired" — needs update. | **PARTIAL** |
| 8 | Policy semantica QES vs AdES | OpenAPI has some qualification docs (lines 647-648) but preset documentation not explicit on AdES vs QES distinction. | ❌ NOT DONE |
| 9 | TST dedicata | TSD support exists (`TsdAwareValidatorAdapter`) but no dedicated TST/ASiC-S endpoint. No `.tsr` support. | ❌ NOT DONE |

**Post-correction state after this session:**
- Backlog #2, #5: barrato ✅ con refs commit + test
- Backlog #1: raffinato come residuo (cosa manca vs cosa già esposto)
- Table rows: report, DSS, TSL, formati, qualifica — tutte corrette contro raw docs + codice

## Output Drift

### `outputs/improvement-points-from-siva-2026-06-28.md` — DRIFTED

**Dependencies:** `analyses/siva-vs-sign-verify-2` (modified 2026-06-28, this session), `entities/siva`, `sources/siva-research`
**Generated:** 2026-06-28 (pre-dates code commits `dd9878f`/`4b4c262`/`6912fc5`)

**Four concrete inaccuracies vs current code:**

| Line | Claim | Reality |
|---|---|---|
| 20 | "`ValidationResult` è piatto (`signatureFormat/indication/subIndication/signatureCount` + mappa `reports`)" | Falso. `signatures[]` con `signedBy`/`bestSignatureTime`/`signatureLevel`/`timestamps[]`, `timestamps[]` top-level sono esposti. |
| 25 | "Gap: non esposto [signatureLevel]" | Falso. Esposto via `SignatureSummary.signatureLevel`. |
| 47 | "`AuditService` **non cablato**" | Parzialmente falso. Cablato in `TslRefreshScheduler` (6 call), `AsyncVerificationController`, `TslController`. Mancante in sync path e worker. |
| 58-59 | "[Health indicator] da fare" | Falso. Già implementati. |

**Verdict: `contradicted`** — body contradicts dependencies modified by subsequent code commits. Needs re-synthesis.

### `outputs/plan-verifica-file-tsd-2026-06-28.md` — WEAK EVIDENCE

**Dependencies:** 13 wiki sources. **Generated:** 2026-06-28. The plan references "Phase 4" for report enrichment which progressed since generation (signatures/timestamps mapping completed, signatureLevel exposed). Body not read in full this audit; classified as `weak-evidence` due to dependency age. Recommend re-verifying.

## Truth Escalation

### Escalated: backlog obsolescence claims
- **Support branch:** code-level verification of all 9 points confirmed via `codegraph_explore` + grep
- **Attack branch:** found output artifact contradicts corrected wiki (see Output Drift above)
- **Verdict:** `supported` — the backlog in `analyses/siva-vs-sign-verify-2` (post-correction) is accurate against code state; the downstream output `improvement-points-from-siva` is `contradicted` and needs update.

### Escalated: entity/siva "not yet wired" statement
- **Evidence:** `TslRefreshScheduler` line 41, 50, 69, 78, 85, 104, 110, 112, 122 — 6× `audit.log` calls. `AsyncVerificationController` line 110 — 1×. `TslController` — 1×. Total 13 files reference `audit`.
- **Verdict:** `weakened` — the statement "not yet wired into operational paths" is outdated. Audit IS partially operational. Update recommended.

## Dependency Chain Integrity

All wiki dependencies resolve: ✅ 20/20 wikilinks to wiki pages, 3/3 raw source refs, 2 output refs exist on disk. No broken links.

## Recommendations

1. 🔴 **Re-synthesize** `outputs/improvement-points-from-siva-2026-06-28.md` — update body to reflect code state (4 inaccuracies). Or mark as deprecated and replace with wiki analysis page as authoritative.
2. 🔴 **Update** `entities/sign-verify-2.md` §"Known gaps" — remove "AuditService not yet wired" or note partial state.
3. 🟡 **Re-verify** `outputs/plan-verifica-file-tsd-2026-06-28.md` — check if Phase 4 completion changes plan scope.
4. 🟢 **Implement** backlog #3 (signed report) or #4 (hashcode) — only genuinely-not-done items with substantial new work.
