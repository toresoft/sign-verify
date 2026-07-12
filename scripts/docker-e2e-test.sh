#!/usr/bin/env bash
#
# End-to-end smoke test against the built Docker image.
#
# Boots the image together with PostgreSQL, waits for readiness, then drives a
# real signature verification through the HTTP API using the auto-generated
# bootstrap API key. This exercises the container the way it actually runs
# (entrypoint, wiring, DB, auth, endpoints) — the kind of check that catches a
# broken ENTRYPOINT or misconfigured profile that unit/integration tests miss.
#
# Trust-independent: the `docker` profile skips the EU LOTL/TSL load, so the
# assertions only require a well-formed response (HTTP 200 + a parseable report
# with a detected signature), never a TOTAL_PASSED trust outcome.
#
# Usage:
#   IMAGE=sign-verify:e2e scripts/docker-e2e-test.sh
#
# Requires: docker, curl, jq.
set -euo pipefail

IMAGE="${IMAGE:-sign-verify:e2e}"
PORT="${PORT:-18080}"
NET="sv-e2e-net"
PG="sv-e2e-pg"
APP="sv-e2e-app"
FIXTURE="${FIXTURE:-src/test/resources/assets/cades/cades-bes-signeddata-enveloping.p7m}"
BASE_URL="http://localhost:${PORT}"

cleanup() {
  echo "::group::sign-verify container logs"
  docker logs "$APP" 2>&1 || true
  echo "::endgroup::"
  docker rm -f "$APP" "$PG" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo ">> Using image: $IMAGE"
[ -f "$FIXTURE" ] || { echo "!! Fixture not found: $FIXTURE"; exit 1; }

docker network create "$NET" >/dev/null

echo ">> Starting PostgreSQL"
docker run -d --name "$PG" --network "$NET" \
  -e POSTGRES_DB=signverify -e POSTGRES_USER=signverify -e POSTGRES_PASSWORD=signverify \
  postgres:16-alpine >/dev/null

echo ">> Waiting for PostgreSQL to accept connections"
for i in $(seq 1 30); do
  if docker exec "$PG" pg_isready -U signverify -d signverify >/dev/null 2>&1; then
    echo "   PostgreSQL ready"
    break
  fi
  [ "$i" -eq 30 ] && { echo "!! PostgreSQL did not become ready"; exit 1; }
  sleep 2
done

echo ">> Starting sign-verify container"
MASTER_KEY="$(head -c 32 /dev/urandom | base64)"
docker run -d --name "$APP" --network "$NET" -p "${PORT}:8080" \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${PG}:5432/signverify" \
  -e SPRING_DATASOURCE_USERNAME=signverify \
  -e SPRING_DATASOURCE_PASSWORD=signverify \
  -e APP_SECRET_MASTER_KEY="$MASTER_KEY" \
  "$IMAGE" >/dev/null

echo ">> Waiting for application readiness"
ready=""
for i in $(seq 1 60); do
  status="$(curl -fsS "${BASE_URL}/actuator/health/readiness" 2>/dev/null | jq -r '.status' 2>/dev/null || true)"
  if [ "$status" = "UP" ]; then ready=1; echo "   Application readiness: UP"; break; fi
  sleep 2
done
[ -n "$ready" ] || { echo "!! Application did not reach readiness UP"; exit 1; }

echo ">> Reading bootstrap API key"
api_key=""
for i in $(seq 1 15); do
  api_key="$(docker exec "$APP" cat /var/lib/sign-verify/bootstrap-api-key.txt 2>/dev/null | tr -d '\r\n' || true)"
  [ -n "$api_key" ] && break
  sleep 1
done
[ -n "$api_key" ] || { echo "!! Bootstrap API key file not found"; exit 1; }
echo "   Bootstrap key acquired (${api_key:0:11}...)"

echo ">> POST ${BASE_URL}/api/v1/verifications  (file=$FIXTURE)"
resp="$(mktemp)"
code="$(curl -sS -o "$resp" -w '%{http_code}' \
  -H "X-API-Key: ${api_key}" \
  -F "file=@${FIXTURE}" \
  "${BASE_URL}/api/v1/verifications")"

echo "   HTTP ${code}"
jq . "$resp" || { echo "!! Response body is not valid JSON"; exit 1; }
[ "$code" = "200" ] || { echo "!! Expected HTTP 200, got ${code}"; exit 1; }

count="$(jq -r '.signatureCount // 0' "$resp")"
fmt="$(jq -r '.signatureFormat // "?"' "$resp")"
ind="$(jq -r '.indication // ""' "$resp")"

[ "$count" -ge 1 ] || { echo "!! Expected signatureCount >= 1, got ${count}"; exit 1; }
[ -n "$ind" ] || { echo "!! Missing indication in report"; exit 1; }

echo ">> E2E OK — signatureCount=${count} signatureFormat=${fmt} indication=${ind}"
