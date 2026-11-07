# syntax=docker/dockerfile:1
# =============================================================================
# Build stage — compile and produce an exploded (layered) Spring Boot jar.
# Java bytecode is platform-neutral, so a glibc Maven image is fine here even
# though the final runtime is musl/Alpine.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency layer: cached as long as pom.xml is unchanged.
COPY pom.xml .
RUN mvn -B -e -ntp dependency:go-offline

# Application sources.
COPY src ./src
RUN mvn -B -e -ntp -DskipTests clean package \
 && java -Djarmode=tools -jar target/sign-verify-2.jar extract --layers --destination target/extracted

# =============================================================================
# JRE-build stage — craft a minimal, musl-linked custom runtime with jlink.
# Built on the Alpine JDK so the produced runtime links against musl and runs
# natively on the bare Alpine runtime below. This image (which still ships
# gnupg/sqlite/coreutils via the Temurin base) is thrown away; only /javaruntime
# is copied forward.
# =============================================================================
FROM eclipse-temurin:21-jdk-alpine AS jre-build

# Explicit, curated module set. Reflection-heavy stacks (Spring, Hibernate, DSS)
# defeat jdeps' static analysis, so the list is maintained by hand:
#   java.xml.crypto  → XAdES / XMLDSig signature verification (DSS core)
#   jdk.crypto.ec    → ECDSA, required for eIDAS signatures
#   java.desktop     → AWT/imaging classes pulled in by PDFBox (PAdES)
#   java.naming/sasl → LDAP retrieval of CRL/AIA
#   java.sql         → JDBC / JPA
#   java.management  → JMX, Micrometer, actuator
#   jdk.net          → jdk.net.Sockets, referenced by Apache HttpClient 5's
#                      DefaultHttpClientConnectionOperator static initializer;
#                      without it DSS cannot download LOTL/TSL over HTTPS
#   jdk.unsupported  → sun.misc.Unsafe (Netty, Hibernate, etc.)
#   jdk.zipfs        → ZIP NIO filesystem for ASiC containers
#   jdk.localedata   → Italian locale (trimmed to en,it below)
RUN "$JAVA_HOME/bin/jlink" \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.jfr,jdk.management,jdk.net,jdk.unsupported,jdk.zipfs,jdk.localedata \
      --include-locales=en,it \
      --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
      --output /javaruntime

# Add the public intermediate CA certificates that some national TSL endpoints
# omit from their TLS chain (e.g. eidas.gov.ie sends only the leaf). Their roots
# are already in cacerts; without the intermediates DSS fails to download those
# Trusted Lists with "PKIX path building failed". Imported into the *jlink*
# runtime truststore so the default JSSE trust manager (used by DSS's HTTP
# client) can build the path. See docker/tls-certs/README.md.
COPY docker/tls-certs/*.pem /tmp/tls-certs/
RUN set -eu; \
    cacerts="/javaruntime/lib/security/cacerts"; \
    for c in /tmp/tls-certs/*.pem; do \
      alias="extra-$(basename "$c" .pem)"; \
      keytool -importcert -noprompt -trustcacerts \
        -keystore "$cacerts" -storepass changeit \
        -alias "$alias" -file "$c"; \
    done; \
    rm -rf /tmp/tls-certs

# =============================================================================
# Runtime stage — bare Alpine, no package manager bloat, non-root, hardened.
# No gnupg/sqlite/coreutils: only the handful of native libs the custom runtime
# needs. fontconfig/freetype/ttf-dejavu back the java.desktop (AWT) module so
# PDFBox font handling never trips on a missing libfontconfig.
# =============================================================================
FROM alpine:3.24 AS runtime

# The version floors carry fixes that the base image does not ship yet:
#   libcrypto3/libssl3 >= 3.5.8-r0 — the 2026-08 OpenSSL advisory batch
#     (CVE-2026-14456/14457, -18798, -54874, -63072..63076, -75803).
#   libexpat >= 2.9.0-r0 — CVE-2026-77214 (heap buffer over-read in
#     XML_ParseBuffer) and CVE-2026-102633 (integer overflow in expat_realloc),
#     on top of the earlier CVE-2026-66046, -76641, -76956, -76957 and -93990.
#     libexpat is not requested directly; it arrives as a fontconfig dependency,
#     and naming it here raises the floor on that transitive install. 3.24 now
#     ships 2.10.0-r0, so it no longer needs edge.
#   libpng >= 1.6.59-r0 — CVE-2026-46675 (use-after-free of zlib input in
#     png_read_end after incomplete zTXt/iTXt/iCCP decompression). Pulled in by
#     freetype.
#   zlib >= 1.4-r0 — CVE-2026-4738 (out-of-bounds memory access), on top of the
#     earlier CVE-2026-85091 (heap buffer overflow in 1.3.1.2 through 1.3.2).
#     Part of the base image, which still carries 1.3.2-r1. 1.4 keeps the
#     libz.so.1 soname, so dependents need no rebuild.
#   musl >= 1.2.6-r6 — CVE-2026-107891. Part of the base image, which carries
#     1.2.6-r3; 3.24 has no r6 yet, so musl and musl-utils (which must match it)
#     come from edge through the @edge tag: an untagged package never resolves
#     from a tagged repository, so everything else stays on 3.24. Same 1.2.6
#     upstream release, so the
#     ABI the jlink runtime links against is unchanged. Drop the tag and keep the
#     floor once 3.24 has 1.2.6-r6.
# `apk upgrade` alone does not get there: the alpine:3.24 digest moves only on
# point releases and there is no 3.25 branch yet, so CI's layer cache
# (--cache-from / type=gha) replays this step and keeps the stale revisions.
# Editing the constraints invalidates the cached layer and forces the upgrade.
# Floors, not pins: later revisions satisfy them.
RUN echo '@edge https://dl-cdn.alpinelinux.org/alpine/edge/main' >> /etc/apk/repositories \
 && apk upgrade --no-cache \
 && apk add --no-cache fontconfig freetype ttf-dejavu \
      'libcrypto3>=3.5.8-r0' 'libssl3>=3.5.8-r0' 'libexpat>=2.9.0-r0' \
      'libpng>=1.6.59-r0' 'zlib>=1.4-r0' \
      'musl@edge>=1.2.6-r6' 'musl-utils@edge>=1.2.6-r6' \
 && addgroup -g 10001 app \
 && adduser -u 10001 -G app -s /sbin/nologin -D -H app \
 && mkdir -p /var/lib/sign-verify/dss-cache /var/lib/sign-verify/jobs /var/lib/sign-verify/extract-tmp \
 && chown -R app:app /var/lib/sign-verify

# Custom runtime.
ENV JAVA_HOME=/opt/java/runtime
ENV PATH="$JAVA_HOME/bin:$PATH"
COPY --from=jre-build /javaruntime "$JAVA_HOME"

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
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health/liveness || exit 1

# Exec form → app is PID 1 and receives SIGTERM for graceful shutdown.
# `extract` (jarmode=tools) drops a slim runnable jar named after the artifact
# plus a lib/ dir here; its manifest Class-Path points at lib/, so `java -jar`
# resolves everything. The name tracks the Maven finalName (sign-verify-2).
ENTRYPOINT ["java", "-jar", "sign-verify-2.jar"]

LABEL org.opencontainers.image.title="sign-verify" \
      org.opencontainers.image.description="eIDAS digital signature verification service (DSS 6.5)" \
      org.opencontainers.image.source="https://github.com/toresoft/sign-verify" \
      org.opencontainers.image.licenses="LGPL-3.0"
