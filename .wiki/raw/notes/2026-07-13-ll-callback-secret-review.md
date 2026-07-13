---
title: "Lessons Learned: server-generated callback secret — implementation review"
type: notes
source: session
ingested: 2026-07-13
tags: [lessons-learned, hmac-callback, code-review, multipart, security, multi-agent-review]
lesson_kind: lessons-learned
lesson_count: 5
category: notes
confidence: high
summary: "High-effort /code-review of feat/server-generated-callback-secret found a real gap: the callbackSecret client-input rejection only checks Spring's multipart file-map, which excludes filename-less form fields, silently bypassing the 400. Also captured: how to scope a review diff on a branch stacked behind unmerged prior features, and a recurring parallel-agent idle/no-report failure mode."
---

# Lessons Learned: server-generated callback secret — implementation review

> Extracted from a `/code-review high` session on 2026-07-13 reviewing the 5 commits
> (`fdb14d2..7844328`) that moved callback-secret generation from client-supplied to
> server-generated in [[entities/sign-verify-2]]. 5 lessons: a diff-scoping technique, a
> multi-agent coordination failure mode, a real Spring multipart gotcha, a matching test
> blind spot, and a review-methodology validation signal.

## Lesson 1: Scope a review diff to the task's baseline commit, not `main`, on a branch stacked behind unmerged prior features

**Category**: pattern
**Context**: Asked to review "the callback secret feature," the obvious first move —
`git diff main...HEAD` — returned an 84-file, ~5000-line diff spanning CI workflow changes,
Docker/e2e scripts, wiki content, and two entirely unrelated already-committed features
(multipart-metadata-parts, profile-by-name) that happened to sit on the same branch ahead
of `main`.
**Symptom**: `git merge-base main HEAD` equaled `main`'s own tip, confirming `main` was a
strict ancestor — so `main..HEAD` genuinely contained all of that unrelated work, not a
divergence artifact.
**Root cause**: The branch was built by stacking multiple already-implemented features
before this one; `main` simply hadn't been fast-forwarded past any of them yet. The task's
own commit log (5 commits) was available from prior session state — "Task 0 — branch +
baseline `fdb14d2`" — but had to be cross-checked against `git log --oneline` rather than
assumed.
**Fix**: Used `git diff fdb14d2...HEAD -- '*.java'` — the documented baseline commit for
this task — to get a clean 14-file, ~400-line diff scoped to exactly the feature under
review.
**Rule**: On a long-lived feature branch, never assume `main...HEAD` is the review scope.
Find the task's actual baseline commit (from session history, a plan doc, or the first
commit whose message introduces the feature) and diff from there; verify with
`git merge-base` that the assumption about ancestry holds before trusting the diff range.

## Lesson 2: Parallel background finder-agents can go idle repeatedly without ever delivering their final report — always have a manual fallback

**Category**: gotcha
**Context**: Dispatched 8 independent finder agents in parallel (background) for an 8-angle
high-effort code review. Each was instructed to report a JSON findings array as its final
message.
**Symptom**: Several agents (`angleReuse`, `angleEfficiency`, `angleSimplify`, `angleA`,
`angleC`, `angleAltitude`) sent `idle_notification` events with no findings content — some
went idle 2-4 times before the actual report arrived. One agent (`angleConventions`) never
sent a content report at all despite 4 explicit re-prompts across ~10 minutes.
**Root cause**: An `idle_notification` in this harness signals "agent finished its turn and
is available," not "agent delivered its final answer" — the two are decoupled. An agent can
go idle after internal work without having actually sent the requested message to the
mailbox.
**Fix**: Treated every `idle_notification` as a prompt to check whether real content had
arrived; when it hadn't, sent an explicit `SendMessage` asking for the JSON report by name
("you must SEND me a message... not just go idle"). For the one agent that never
responded after 4 attempts, abandoned the wait and performed that angle's checks manually
(reading files, `wc -l`, grepping the OpenAPI diff) rather than blocking indefinitely.
**Rule**: When fanning out parallel background agents expecting a specific final report,
don't treat `idle_notification` as completion — wait for actual content, re-prompt
explicitly (and by name) if idle fires without it, and set a personal cutoff (e.g. 3-4
re-prompts) after which you do the work yourself rather than stalling the whole review.

## Lesson 3: Spring's `MultipartHttpServletRequest.getMultiFileMap()` only contains parts sent WITH a `filename=` attribute — a plain form field silently lands elsewhere

**Category**: discovery
**Context**: The new `AsyncVerificationController.submit()` rejects a client-supplied
`callbackSecret` multipart part with 400 by checking
`multipart.getMultiFileMap().containsKey("callbackSecret")`, added specifically to close
off the deprecated client-supplied-secret input path now that the secret is
server-generated (see [[concepts/hmac-callback]]).
**Symptom**: Three independent review angles converged on the same gap: a client sending
`callbackSecret` as an ordinary multipart form field (e.g. `curl -F callbackSecret=shh`,
no `filename=`) is routed by Spring's `StandardMultipartHttpServletRequest.parseRequest()`
to the request's multipart *parameter* names, not into `getMultiFileMap()` — so the
`containsKey` check is `false` and the request succeeds with 202 instead of the intended
400.
**Root cause**: Servlet multipart parsing (and Spring's wrapper over it) distinguishes
"file parts" from "field parts" by the presence of `filename=` in the part's
`Content-Disposition` header, not by part name or content type. Any code that wants to
detect "was part X present at all," regardless of how the client formats it, must not rely
on `getMultiFileMap()` alone.
**Fix (recommended, not yet applied)**: Restore
`@RequestPart(value = "callbackSecret", required = false) String callbackSecret` in the
controller signature and reject it inside `VerificationRequestParts` — Spring's normal
`@RequestPart` binding resolves a part regardless of whether it carries a `filename=`,
so it "fails closed" where the raw `getMultiFileMap()` check "fails open."
**Rule**: To detect "is multipart part X present," bind it via `@RequestPart(required =
false)` (or otherwise consult the full parsed part set), never via
`getMultiFileMap().containsKey(...)` alone — that map excludes any part sent without a
filename.

## Lesson 4: `MockMultipartFile` always registers into `getMultiFileMap()` regardless of an empty filename string — tests using it cannot catch the Lesson 3 gap

**Category**: gotcha
**Context**: The existing regression test for the callbackSecret rejection,
`postAsync_callbackSecretPart_isRejected400`, uses
`new MockMultipartFile("callbackSecret", "", "text/plain", "shh".getBytes())` — an empty
string, not null, for the filename argument.
**Symptom**: The test passes, giving the impression the rejection is fully covered — but
`MockMultipartFile` populates Spring's file map regardless of that empty filename string,
so the test only exercises the file-part branch of multipart parsing, never the
plain-form-field branch that actually bypasses the check (Lesson 3).
**Root cause**: `MockMultipartFile`'s constructor doesn't distinguish "no filename
attribute at all" (the real-world bypass case) from "filename attribute present but
empty" (what the mock always produces) — both end up registered as a file part in Spring's
test multipart support.
**Fix**: None yet — flagged as a review finding (test-coverage) alongside Lesson 3's fix.
A regression test for the field-part path needs to construct the multipart request more
directly (e.g. via `MockMultipartHttpServletRequestBuilder` with a manually added
`MockPart`, which lacks a filename) rather than `MockMultipartFile`.
**Rule**: `MockMultipartFile` in Spring MVC tests always behaves like a "file part" for
multipart-map routing purposes, even with an empty filename string — it cannot be used to
test the "field part with no filename" code path. Use `MockPart` (no filename concept) or a
raw multipart body when the code under test distinguishes file parts from field parts.

## Lesson 5: When several independent review angles converge on the same finding without coordination, that convergence is itself a stronger signal than any single angle's confidence

**Category**: pattern
**Context**: In the 8-angle parallel dispatch (line-by-line scan, removed-behavior audit,
cross-file trace, reuse, simplification, efficiency, altitude, conventions), 5 of the 8
angles — approached from completely different framings (a security-review scan, a
"what invariant did this diff remove" audit, a cross-file call-site trace, a
simplification pass, and an architecture/altitude pass) — independently surfaced the exact
same root defect (Lesson 3), each with its own supporting technical reasoning.
**Symptom**: No single angle's prompt mentioned the other angles' framing, yet all five
converged on `AsyncVerificationController.java:82-83` and the same Spring mechanism.
**Root cause**: The bug sits at a genuine seam — client-input validation for a security
field — that shows up under correctness, architecture, and simplification framings alike,
because it's simultaneously a functional bug, a duplicated/split validation mechanism, and
an under-tested code path.
**Fix**: Treated the 5-way convergence as sufficient corroboration to mark the finding
CONFIRMED without spawning a dedicated verifier agent for it, reserving verification
effort for the lower-confidence, single-angle claims (a hypothetical `CallbackWorker` NPE,
an "the leaked secret overrides the generated one" framing that turned out to be
factually wrong on inspection).
**Rule**: In a multi-angle parallel review, unprompted convergence of 3+ independent
angles on the same file:line is a strong CONFIRMED signal — spend verification budget on
the single-angle claims instead, and specifically re-check any claim whose *impact*
framing differs between agents (here, one agent's "the secret could be overridden" was
refuted while the underlying "the check bypasses" claim held).

## Related
- [[concepts/hmac-callback]] — updated with the server-generated secret + multipart
  validation gotcha from Lesson 3.
