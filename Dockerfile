# syntax=docker/dockerfile:1
# =============================================================================
# Build stage — compile and produce an exploded (layered) Spring Boot jar.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer: cached as long as pom.xml is unchanged.
COPY pom.xml .
RUN mvn -B -e -ntp dependency:go-offline

# Application sources.
COPY src ./src
RUN mvn -B -e -ntp -DskipTests clean package \
 && java -Djarmode=layertools -jar target/sign-verify-2.jar extract --destination target/extracted

# =============================================================================
# Runtime stage — minimal, non-root, hardened.
# =============================================================================
FROM eclipse-temurin:21-jre-jammy AS runtime

# - Apply OS security updates.
# - Install only curl (needed by HEALTHCHECK), no recommended extras.
# - Create an unprivileged user and the writable data directory up front.
RUN apt-get update \
 && apt-get upgrade -y \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -g 10001 app \
 && useradd -u 10001 -g app -s /usr/sbin/nologin -M app \
 && mkdir -p /var/lib/sign-verify/dss-cache /var/lib/sign-verify/jobs \
 && chown -R app:app /var/lib/sign-verify

WORKDIR /app

# Copy the exploded layers most-stable-first so image layers cache well.
COPY --from=build --chown=app:app /build/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/application/ ./

# Numeric uid:gid so orchestrators can enforce runAsNonRoot.
USER 10001:10001

EXPOSE 8080

# JDK 21 already honours cgroup limits (UseContainerSupport on by default).
# Extra/operator JVM flags can be appended through JAVA_TOOL_OPTIONS at runtime.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# Exec form → app is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

LABEL org.opencontainers.image.title="sign-verify" \
      org.opencontainers.image.description="eIDAS digital signature verification service (DSS 6.4)" \
      org.opencontainers.image.source="https://hub.docker.com/r/toresoft/sign-verify" \
      org.opencontainers.image.licenses="Apache-2.0"
