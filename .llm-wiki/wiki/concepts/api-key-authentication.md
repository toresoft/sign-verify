---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-005
---

# API key authentication

The always-on authentication mechanism of [[entities/sign-verify-2]] ([[entities/api_key|api_key]] is the backing table). A request carries `X-API-Key: sv_<prefix>_<body>`; the `ApiKeyAuthenticationFilter` does an O(1) prefix-indexed lookup, verifies the bcrypt hash, checks `enabled` + `expires_at`, and populates an `API_KEY` principal.

Runs **before** the OAuth2 chain in the filter chain; if a valid API key is present, OAuth (if any) is skipped (both present → API key prevails, logged as warning). Missing credentials → `401 auth.missing-credentials` ([[concepts/problemjson|problem+json]]).

A **bootstrap API key** (PRIVILEGED) is generated on first startup to seed administrative access; the **last enabled PRIVILEGED key cannot be removed**.

## Related
- [[entities/api_key]] · [[entities/sign-verify-2]]
- [[concepts/oauth2-resource-server]] · [[concepts/problemjson]]
