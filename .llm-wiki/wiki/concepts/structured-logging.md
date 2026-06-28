---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
---

# Structured logging

The primary observability pillar of [[entities/sign-verify-2]]: application logs emitted as **JSON to STDOUT** (logback), enriched with per-request context via **MDC** (correlation id, principal id). Consumed by an external collector (Loki/ELK). This is currently the *de facto* operational traceability mechanism — see [[entities/audit_log]]'s wiring gap.

See also [[concepts/problemjson]] (error shape) and the logging/audit source [[sources/SRC-2026-06-27-007]].

## Related
- [[entities/sign-verify-2]] · [[entities/audit_log]]
- [[concepts/problemjson]]
