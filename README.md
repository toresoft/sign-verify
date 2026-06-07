# sign-verify

REST service for **eIDAS electronic-signature verification** (PAdES / CAdES / XAdES /
JAdES / ASiC) built on Spring Boot 3.4 and the EU **DSS 6.4** library, with EU Trusted
List (LOTL/TSL) support, API-key + OAuth2 authentication, async jobs with HMAC callbacks,
audit logging and observability.

- Design: `docs/superpowers/specs/2026-06-07-sign-verify-design.md`
- Implementation plan: `docs/superpowers/plans/2026-06-07-sign-verify-implementation.md`

## Requirements

- JDK 21 (e.g. via SDKMAN), Maven 3.9+
- Docker / Docker Compose (for the containerised dev stack and image build)

## Build & test

```bash
mvn clean verify        # unit + integration tests (Testcontainers) + Spotless + JaCoCo
mvn spotless:apply      # auto-format (Google Java Format) before committing
```

## Run locally (host, H2 in-memory)

```bash
mvn clean package
java -Dspring.profiles.active=dev -jar target/sign-verify-2.jar
# bootstrap API key is written to ./target/bootstrap-api-key.txt on first start
```

## Development with Docker (app + PostgreSQL)

The `docker` Spring profile is tuned for the container: OAuth disabled, a dev master
key, TSL load skipped, data under `/var/lib/sign-verify`.

```bash
docker compose up --build
# read the bootstrap (PRIVILEGED) API key generated on first boot:
docker compose exec app cat /var/lib/sign-verify/bootstrap-api-key.txt

curl -H "X-API-Key: <bootstrap-key>" http://localhost:8080/actuator/health
```

Swagger UI: <http://localhost:8080/swagger-ui/index.html>

## Production image

The image is multi-stage and hardened: JRE-only runtime, non-root user (uid 10001),
OS security updates applied, exec-form entrypoint (PID 1, graceful SIGTERM), container
healthcheck. Run it read-only with all capabilities dropped:

```bash
cp .env.example .env        # fill in DB, master key, OAuth issuer, OJ keystore password
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` enables `read_only`, `no-new-privileges`, `cap_drop: ALL`,
a tmpfs `/tmp`, resource limits and a readiness healthcheck. It expects an external,
managed PostgreSQL.

Generate a master key:

```bash
openssl rand -base64 32
```

### Key configuration (environment variables)

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | PostgreSQL connection | H2 in-memory |
| `APP_SECRET_MASTER_KEY` | base64 256-bit key encrypting stored secrets | _(required)_ |
| `APP_SECURITY_OAUTH_ENABLED` | enable OAuth2 JWT resource server | `true` |
| `APP_SECURITY_OAUTH_ISSUER_URI` | OIDC issuer (required if OAuth enabled) | _(empty)_ |
| `APP_OJ_KEYSTORE_PASSWORD` | EU Official Journal keystore password | _(empty)_ |
| `JAVA_TOOL_OPTIONS` | extra JVM flags | container ergonomics |

## CI/CD — publishing to Docker Hub

Two equivalent pipelines are provided — use whichever host you push to:

- **GitLab CI** — `.gitlab-ci.yml`
- **GitHub Actions** — `.github/workflows/ci.yml`

Both run `validate → test → build → package → security` and the **package** stage builds
the image and pushes to Docker Hub as `toresoft/sign-verify`:

- every default-branch pipeline → `:<short-sha>` and `:latest`
- every git tag `vX.Y.Z` → `:<short-sha>` and `:<tag>`

Configure these credentials — on GitLab as masked CI/CD variables (Settings → CI/CD →
Variables), on GitHub as repository secrets (Settings → Secrets and variables → Actions):

| Name | Value |
|---|---|
| `DOCKERHUB_USERNAME` | Docker Hub account/namespace |
| `DOCKERHUB_TOKEN` | Docker Hub access token (Account → Security) |

The **security** stage scans the pushed image with Trivy (HIGH/CRITICAL) and runs an
OWASP dependency check.

### Build & push manually

```bash
docker build -t toresoft/sign-verify:dev .
echo "$DOCKERHUB_TOKEN" | docker login -u toresoft --password-stdin
docker push toresoft/sign-verify:dev
```
