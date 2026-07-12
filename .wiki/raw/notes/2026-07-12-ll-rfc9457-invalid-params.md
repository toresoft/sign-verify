---
title: "Lessons Learned: RFC 9457 invalid-params for field-level validation errors"
type: notes
source: session
ingested: 2026-07-12
tags: [lessons-learned, problemjson, validation, jackson3, design-first]
lesson_kind: lessons-learned
lesson_count: 4
category: notes
confidence: high
summary: "Validation errors now carry an RFC 9457 invalid-params array; surfacing the offending field for malformed bodies needs JacksonException.getPath() (not MismatchedInputException), and path/query type-mismatch errors were silently 500 until handled."
---

# Lessons Learned: RFC 9457 invalid-params for field-level validation errors

> Extracted from session on 2026-07-12. 4 lessons covering field-level validation
> error responses, a Jackson 3 exception-hierarchy gotcha, contract/test discipline,
> and an unhandled param type-mismatch that was leaking 500s.

## Lesson 1: Validation errors must name the field via RFC 9457 `invalid-params`

**Category**: pattern
**Context**: A `POST /api/v1/api-keys` with a missing field returned `detail: "must not be null"` with no indication of *which* field — useless to clients.
**Symptom**: `GlobalExceptionHandler.handleValidation` flattened all errors with `getBindingResult().getAllErrors().stream().map(getDefaultMessage())`, discarding field names.
**Root cause**: `getDefaultMessage()` alone drops the binding location; `getFieldErrors()` (→ `FieldError.getField()`) carries it.
**Fix**: Build an `invalid-params` array (`[{name, reason}]`) from `getFieldErrors()` + `getGlobalErrors()`, add it as a top-level problem member, and set `detail` to a human summary (`name: must not be null`). Documented the member in the OpenAPI `Problem` schema.
**Rule**: For a problem+json validation error, emit the RFC 9457 §3.1 `invalid-params` array (`{name, reason}`) — one entry per rejected field — never a single flat message.

## Lesson 2: Surfacing the offending field for a malformed body needs `JacksonException.getPath()`, not `MismatchedInputException`

**Category**: gotcha
**Context**: Wanted `handleNotReadable` (type mismatch / bad enum) to also name the field, not just return `"malformed request body"`.
**Symptom**: A test posting an invalid enum (`{"name":"k","role":"NOPE"}`) produced 400 but **no** `invalid-params[0].name` when the handler only matched `ex.getCause() instanceof MismatchedInputException`.
**Root cause**: The OpenAPI-generated enum's `@JsonCreator fromValue(...)` throws a plain `IllegalArgumentException`; Jackson 3 wraps it as a broader `JacksonException` (value-instantiation), **not** a `MismatchedInputException`. So the narrow `instanceof` missed the bad-enum case entirely — a missing-field bean-validation error (a different path, `MethodArgumentNotValidException`) still worked, masking the gap.
**Fix**: Match `ex.getCause() instanceof JacksonException je && !je.getPath().isEmpty()` and build a dotted/indexed path from `je.getPath()` (`Reference.getPropertyName()` / `getIndex()`, e.g. `items[2].role`). A pure JSON syntax error has an empty path → falls back to `"malformed request body"`, preserving the existing regression pin.
**Rule**: To locate the rejected field on a Jackson-3 read failure, check the base `tools.jackson.core.JacksonException` and its `getPath()` — subclasses like `MismatchedInputException` only cover a subset; creator-thrown exceptions bypass them.

## Lesson 3: Error-body shape changes are a two-front change — OpenAPI schema + end-to-end MockMvc test

**Category**: rule
**Context**: Adding `invalid-params` to the emitted problem body in a design-first project guarded by `OpenApiContractIT`.
**Symptom**: (preventive) A handler-only change would leave the documented `Problem` schema stale and the new branch untested through the real HTTP stack — exactly the coverage gap already flagged for `handleNotReadable` before the Boot 4.1 migration.
**Root cause**: The problem body is built as a raw `Map` in the handler, decoupled from the generated `Problem` DTO, so nothing forces the OpenAPI schema or a test to follow.
**Fix**: Updated `openapi.yaml` `Problem` schema with the `invalid-params` member, and added MockMvc tests (missing required field → `name`; invalid enum → `role`) reusing the existing `MalformedBodyRegressionIT` `@WebMvcTest` harness.
**Rule**: Any change to the problem+json body shape must be mirrored in the OpenAPI `Problem` schema and exercised by a full-stack MockMvc test — unit-testing the handler class alone does not prove the wire format.

## Lesson 4: Path/query type-mismatch had no handler and leaked 500 — extend invalid-params to parameters

**Category**: gotcha
**Context**: After field-level body validation worked, extended the same treatment to `@RequestParam`/`@PathVariable` errors.
**Symptom**: `GET /api/v1/api-keys?page=abc` (page is `Integer`) and `DELETE /api/v1/api-keys/not-a-uuid` (`{id}` is a `UUID`) returned **500 internal-error** — a client input error masquerading as a server fault. The docs already claimed "bad query param → 400", so reality contradicted the contract.
**Root cause**: `GlobalExceptionHandler` handled `MethodArgumentNotValidException` (body) and `HttpMessageNotReadableException` but had **no** handler for `MethodArgumentTypeMismatchException`, so param coercion failures fell through to the catch-all `Exception` → 500.
**Fix**: Added handlers for `MethodArgumentTypeMismatchException` (name = `ex.getName()`, reason = `"must be a valid " + requiredType.getSimpleName()`), `MissingServletRequestParameterException`, and `HandlerMethodValidationException` (Spring 6.1+/Boot 4 method-level validation, via `getParameterValidationResults()` → `ParameterValidationResult.getMethodParameter().getParameterName()` + `getResolvableErrors()`). All emit 400 with the same `invalid-params` shape.
**Rule**: Field-level error reporting isn't done at the request body — path/query coercion failures (`MethodArgumentTypeMismatchException`) and method-level param validation (`HandlerMethodValidationException`) need their own handlers, or they silently degrade to 500.
