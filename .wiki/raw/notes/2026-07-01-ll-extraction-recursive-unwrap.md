---
title: "Lessons Learned: recursive container unwrap + optional filename in /extractions"
type: lessons-learned
source: session
date: 2026-07-01
tags: [lessons-learned, dss, extraction, circuit-breaker, rfc5544-tsd, bouncycastle, appexception, recursion, testing]
lesson_count: 4
category: notes
confidence: high
compiled: 2026-07-01
summary: "Making /api/v1/extractions recursively unwrap nested signed containers exposed a class of latent bug: recursion re-feeds internal originals as fresh documents, so every unguarded DSS call that throws a raw (non-AppException) exception poisons the dssExtraction circuit breaker. Plus: BouncyCastle TSD unwrap needs a syntactically valid temporalEvidence in test fixtures, AppException.getMessage() is the title (assert on getDetail()), and depth-aware leaf-vs-error with MAX_DEPTH is the termination pattern."
---

# Lessons Learned: recursive container unwrap + optional filename in /extractions

> Extracted from session on 2026-07-01. 4 lessons from making `POST /api/v1/extractions`
> deduce an absent filename and recursively unwrap nested signed containers
> (TSD → p7m/CAdES → … → leaf). See [[concepts/circuit-breaker]],
> [[concepts/file-extraction]], [[entities/dssextractionadapter]],
> [[entities/tsdawareextractionadapter]], [[concepts/rfc5544-tsd]],
> [[concepts/problemjson]].

## Lesson 1: Recursion re-feeds internal originals as fresh documents — every unguarded DSS call then poisons the shared circuit breaker

**Category**: pattern
**Context**: `RecursiveExtractionAdapter` (renamed from `TsdAwareExtractionAdapter`) now peels a container, then feeds each extracted "original" back into `DssExtractionAdapter.extract` as if it were a brand-new top-level document, recursing to the leaf.
**Symptom**: Two independent failures surfaced only under recursion, not before: (a) a PAdES byte-range revision slice fed back in made DSS's `getRevisions()`/`getSignatures()` throw a raw `eu.europa.esig.dss.model.DSSException`; (b) a detached-signature original is returned as a `DigestDocument` whose `openStream()` throws `UnsupportedOperationException`. Both escaped as raw exceptions and were **counted** by the `dssExtraction` circuit breaker, eventually opening it and returning 503 for all extraction traffic.
**Root cause**: The breaker is configured `resilience4j...dssExtraction.ignoreExceptions: [AppException]` precisely so bad input can't trip it (else a burst of malformed files = DoS for legitimate traffic). `DssExtractionAdapter` translated `fromDocument()` and `getOriginalDocuments()` failures to `AppException`, but left `getSignatures()` and the per-original `openStream().readAllBytes()` loop throwing raw exceptions. Non-recursive callers never reached those branches with bad bytes; recursion is the first caller that does.
**Fix**: Wrap every DSS call reachable on this path in `try/catch → AppException` (`getSignaturesOrThrow(...)`, and the read loop `catch → AppException.badRequest("cannot read extracted document: ...")`). At `depth==0` this yields a clean 400; at `depth>0` the recursion's own `catch (AppException)` degrades it to a raw leaf — bounded and safe.
**Rule**: On any method guarded by a circuit breaker with `ignoreExceptions: [AppException]`, EVERY downstream library call must translate raw exceptions to `AppException` — an unguarded call is a latent breaker-poisoning DoS, and code that recursively re-feeds its own outputs will eventually exercise every such branch.

## Lesson 2: A hand-built RFC 5544 TSD needs a syntactically valid `temporalEvidence` before BouncyCastle will unwrap it

**Category**: gotcha
**Context**: A test built a minimal TSD (`ContentInfo` with OID `id-aa-timeStampedData` wrapping `[version, content]`) to exercise the recursive unwrap.
**Symptom**: `new CMSTimeStampedData(bytes)` / `TimeStampedData` threw `ArrayIndexOutOfBoundsException: Index 2 out of bounds for length 2`, silently swallowed by `tryUnwrapTsd`'s `catch (Exception)` — so the adapter never detected the (legitimately TSD-shaped) bytes as a TSD at all and fell through to the delegate.
**Root cause**: `org.bouncycastle.asn1.cms.TimeStampedData`'s constructor eagerly reads `seq.getObjectAt(2)` for the mandatory `temporalEvidence` field after `version` + `content`. A 2-element sequence that omits it blows up. A routing-only probe (`Rfc5544TsdRoutingTest`) gets away with omitting it because it never constructs `CMSTimeStampedData`; a test that actually unwraps does.
**Fix**: Encode a syntactically valid (not cryptographically valid) `temporalEvidence`: `[0] IMPLICIT TimeStampTokenEvidence` → one `TimeStampAndCRL` → a minimal `ContentInfo` (arbitrary OID, no content). `ContentInfo.getInstance` permits a 1-element sequence and never validates the inner content type, so no real RFC 3161 token is needed.
**Rule**: BouncyCastle CMS/TSD parsers validate structure eagerly in the constructor — test fixtures that will actually be unwrapped must be structurally complete, not just "shaped right"; a routing-only fixture is not a parsing fixture.

## Lesson 3: `AppException.getMessage()` returns the RFC 9457 title, not the detail — assert on `getDetail()`

**Category**: correction
**Context**: A unit test asserted the MAX_DEPTH guard fired via `assertThatThrownBy(...).hasMessageContaining("max depth")`.
**Symptom**: Test failed — the exception was the right type but `getMessage()` was `"Bad Request"`, not the descriptive string.
**Root cause**: `AppException extends RuntimeException` with `super(title)`, so `getMessage()` is always the fixed problem+json title. The descriptive text (`"extraction nesting exceeds max depth 10"`) lives in a separate `detail` field exposed via `getDetail()`.
**Fix**: `catchThrowableOfType(..., AppException.class)` then `assertThat(thrown.getDetail()).contains("max depth")`. Matches the convention already used by `AsyncJobServiceTest`/`TslControllerTest`.
**Rule**: For RFC 9457 exceptions where `getMessage()==title`, assert descriptive content on the domain accessor (`getDetail()`), never on `getMessage()`/`hasMessageContaining`.

## Lesson 4: Recursive container unwrap terminates via MAX_DEPTH + depth-aware leaf-vs-error, not via content inspection

**Category**: pattern
**Context**: Designing how the recursion stops, given heterogeneous nesting (TSD → CAdES → ASiC → file) and pathological inputs (PAdES revision chains, crafted deep nesting).
**Symptom/risk**: Without a bound, PAdES revisions or hand-crafted nesting could recurse unbounded; without a way to tell "leaf" from "error", a plain file at the bottom looks like a parse failure.
**Root cause / design**: A non-container reached at `depth>0` is a legitimate leaf; the same parse failure at `depth==0` is genuine bad user input. Content sniffing can't reliably distinguish "signed container" from "plain file" up front — the DSS extractor's own parse outcome is the signal.
**Fix**: Drive recursion in a private helper (keeps `@CircuitBreaker` on the public entry only, so recursion doesn't re-enter the proxy). `depth==0` parse error → propagate (400); `depth>0` parse error → treat bytes as a raw leaf with content-sniffed name/mime; hard `MAX_DEPTH=10` bound → `AppException.badRequest`. Report the OUTERMOST container's format in `X-Signature-Format`.
**Rule**: For recursive unwrap, use the extractor's parse outcome (not pre-inspection) as the leaf/container signal, make the depth==0-vs-depth>0 distinction explicit, always bound depth, and keep resilience annotations on the public entry point so internal recursion bypasses the proxy.
