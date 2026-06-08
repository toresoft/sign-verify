# sign-verify

REST service for **eIDAS electronic-signature verification** (PAdES, CAdES,
XAdES, JAdES, ASiC) built on Spring Boot and the EU **DSS 6.4** library, with EU
Trusted List (LOTL/TSL) management.

- 📦 **Source repository:** https://gitlab.com/toresoft/sign-verify
- 📖 **Full documentation:** https://gitlab.com/toresoft/sign-verify/-/tree/main/docs
  ([English](https://gitlab.com/toresoft/sign-verify/-/blob/main/docs/en/README.md) ·
  [Italiano](https://gitlab.com/toresoft/sign-verify/-/blob/main/docs/it/README.md))
- 🐞 **Issues:** https://gitlab.com/toresoft/sign-verify/-/issues

> This page is a quick reference for running the published image. The complete,
> diagram-rich usage guide lives in the [source repository](https://gitlab.com/toresoft/sign-verify).

---

## What it does

- **Signature verification**, synchronous and **asynchronous** (job + HMAC-signed
  HTTP callback).
- **Verification profiles** (`BASIC` / `STANDARD` / `STRICT`) with per-request
  policy overrides.
- **Extraction** of the original document from a signed container.
- **TSL management**: download and mirror of the EU List of Trusted Lists (LOTL),
  scheduled refresh, inspection of trusted certificates.
- **Authentication** via API key (`X-API-Key`) and/or OAuth2 JWT; roles
  `STANDARD` and `PRIVILEGED`.
- **Audit log**, observability (health/readiness, Prometheus metrics, JSON logs),
  automatic job retention and cleanup.

Supported formats: PAdES (PDF), CAdES (CMS), XAdES (XML), JAdES (JSON),
ASiC-S/ASiC-E.

## Image

- **Registry:** `toresoft/sign-verify`
- **Base:** `eclipse-temurin:21-jre-alpine`, runs as non-root (`uid:gid 10001`)
- **Exposed port:** `8080`
- **Writable data path:** `/var/lib/sign-verify` (mount a volume here)

### Tags

| Tag | Meaning |
|-----|---------|
| `latest` | Latest build from the default branch |
| `<version>` | Released version (Git tag, e.g. `0.9.21`) |
| `<short-sha>` | Exact commit build |

## Quick start

A valid `APP_SECRET_MASTER_KEY` (base64 of 32 bytes) is required; generate one
with `openssl rand -base64 32`. By default OAuth is enabled, so either provide
`APP_SECURITY_OAUTH_ISSUER_URI` or disable it with
`APP_SECURITY_OAUTH_ENABLED=false`.

```bash
docker run -d --name sign-verify -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/signverify \
  -e SPRING_DATASOURCE_USERNAME=signverify \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e APP_SECRET_MASTER_KEY="$(openssl rand -base64 32)" \
  -e APP_SECURITY_OAUTH_ENABLED=false \
  -v svdata:/var/lib/sign-verify \
  toresoft/sign-verify:latest
```

On first start, if no `PRIVILEGED` API key exists, a **bootstrap key** is
generated and written to `/var/lib/sign-verify/bootstrap-api-key.txt`
(mode `0600`) — read it, create your own keys, then remove it:

```bash
docker exec sign-verify cat /var/lib/sign-verify/bootstrap-api-key.txt
```

A hardened, production-oriented `docker-compose.prod.yml` (read-only root FS,
dropped capabilities, resource limits) is provided in the
[repository](https://gitlab.com/toresoft/sign-verify/-/blob/main/docker-compose.prod.yml).

## Main environment variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC database URL | in-memory H2 |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | `sa` / *(empty)* |
| `APP_SECRET_MASTER_KEY` | Secret-encryption key, base64 of 32 bytes | *(required)* |
| `APP_SECURITY_OAUTH_ENABLED` | Enable the OAuth2 JWT resource server | `true` |
| `APP_SECURITY_OAUTH_ISSUER_URI` | OIDC issuer (required when OAuth enabled) | *(empty)* |
| `APP_OJ_KEYSTORE_PASSWORD` | EU Official Journal keystore password (LOTL) | *(empty)* |
| `SERVER_PORT` | HTTP port | `8080` |

See the full list and details in the
[configuration guide](https://gitlab.com/toresoft/sign-verify/-/blob/main/docs/en/01-build-configuration.md).

## Health

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness` (UP only once the Trusted Lists
  are loaded)
- Metrics: `GET /actuator/prometheus`

## API

OpenAPI contract is served at `/v3/api-docs`, Swagger UI at
`/swagger-ui/index.html`. Endpoint reference:
[signature verification](https://gitlab.com/toresoft/sign-verify/-/blob/main/docs/en/05-signature-verification.md) ·
[authentication](https://gitlab.com/toresoft/sign-verify/-/blob/main/docs/en/03-authentication.md) ·
[trusted certificates](https://gitlab.com/toresoft/sign-verify/-/blob/main/docs/en/04-trusted-certificates.md).

## License

Apache-2.0.
