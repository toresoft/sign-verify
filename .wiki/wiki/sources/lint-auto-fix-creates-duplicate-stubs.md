---
type: source
title: "wiki_lint auto_fix creates duplicate stubs from link text"
slug: lint-auto-fix-creates-duplicate-stubs
status: insight
created: 2026-06-27
updated: 2026-06-27
category: tool-quirk
volatility: warm
---
# wiki_lint auto_fix creates duplicate stubs from link text
Running `wiki_lint(auto_fix: true)` on a vault where rich kebab-case concept pages already exist (e.g. `concepts/circuit-breaker.md`) but are linked with natural Title-case text (e.g. `[[concepts/circuit-breaker]]`) caused the linter to **auto-create empty stub files named after the link text** (`concepts/Circuit breaker.md`, `status: stub`, "Stub auto-created by lint"). Result: two files per concept — a rich kebab page and a Title-case stub — inflating page count and fragmenting the graph.

Root cause: the linter's dangling-link resolver did not fuzzy-match `[[concepts/circuit-breaker]]` to the existing `circuit-breaker.md`, so it stubbed a new file using the literal link text as both title and filename.

Lessons for this wiki workflow:
1. **Prefer `wiki_lint()` WITHOUT `auto_fix`** when you have already authored pages — read the gap list and create real pages yourself instead of letting lint scatter stubs.
2. **Canonical filenames are kebab-case** (`circuit-breaker.md`); when seeding `[[wikilinks]]` inline, the resolver accepts Title-case/space link text but you should be aware stubs may spawn under the literal text. After authoring, run a non-auto-fix lint to confirm resolution.
3. **Recovery:** `wc -c` classifies files instantly — stubs are ~200–450 bytes (template + frontmatter), rich pages 800B+. Delete the Title-case stubs (`rm`) keeping the kebab rich pages, then `wiki_rebuild_meta`.
4. **Title sanitization:** `wiki_ensure_page` strips some chars from the filename (e.g. `api_key`→`apikey.md`, `problem+json`→`problemjson.md`), which can re-break link resolution if your inline links use the original spelling (`[[entities/api_key]]`). Rename the file to match the link text (`mv apikey.md api_key.md`) before rebuilding meta.

See [[entities/sign-verify-2]], [[concepts/hexagonal-architecture]], [[concepts/circuit-breaker]].
*Category: tool-quirk*
---
*Captured: 2026-06-27*
## Related
_Add links to related pages._