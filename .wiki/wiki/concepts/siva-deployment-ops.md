---
type: concept
category: operations
created: 2026-06-28
updated: 2026-06-28
sources:
  - sources/SRC-2026-06-28-001
  - sources/siva-research
tags: [siva, deployment, ops, tsl, lotl, monitoring, roadmap, systemd, war]
confidence: high
summary: "SiVa 3.x build & deployment (fat JAR / systemd / WAR-per-Tomcat), TSL loader configuration, the April 2026 EU LOTL signer-certificate rotation, monitoring endpoints, report-signing config, statistics, and the 3.11 roadmap."
---

# SiVa deployment & operations

Operational reference for [[entities/siva]]. Sources: [[sources/SRC-2026-06-28-001]] (`docs/siva3/deployment_guide.md`, `LOTL_update_April_2026.md`, `roadmap.md`, `structure_and_activities.md`).

## Requirements & build

Java **11+** (README targets JDK 17 for dev), 2 GB RAM min / 4 GB recommended, 1 core, Ubuntu 16.04 LTS reference. Build: `./mvnw clean install` — **first build up to ~45 min** (deps + vuln checks + unit/IT). Reactor modules: `validation-commons`, `tsl-loader`, `Generic Validation Service`, `TimeStampToken Validation Service`, `Time-mark container Validation Service`, `siva-monitoring`, `siva-statistics`, `siva-webapp`, `siva-sample-application`, integration tests, `siva-distribution`.

## Deploy options

1. **Fat JAR** — `siva-webapp-X.X.X.jar`, port **8080**. `siva-sample-application-X.X.X.jar` runs the demo on :9000.
2. **systemd** — drop a unit (`User≠root`, `JAVA_OPTS=-Xmx320m`, `RUN_ARGS=--server.port=80`), jar at `/var/apps/siva-webapp.jar`, `systemctl start siva-webapp`.
3. **WAR (legacy Tomcat)** — **one Tomcat per SiVa service** (JAR-version-conflict avoidance); WAR to Tomcat **ROOT**; configure `maxPostSize` manually. On startup SiVa creates an `etc/` dir with `siva-keystore.jks` (default location = app root or `$CATALINA_HOME`); override via `DSS_DATA_FOLDER`. **Upgrading SiVa or updating `siva-keystore.jks` requires deleting the "temp" keystore.** External config via `setenv.sh` → `CATALINA_OPTS=-Dspring.config.location=file:/path/application.properties`.

Override any property via external `application.yml`, CLI args, or env. E.g. `server.port=8080`, `siva.http.request.max-request-size-limit=15MB`.

## TSL loader configuration

TSL is used by the **Generic and BDOC validators only**.

| Property | Default | Meaning |
|---|---|---|
| `siva.tsl.loader.url` | `https://ec.europa.eu/tools/lotl/eu-lotl.xml` | EU LOTL URL |
| `siva.tsl.loader.ojUrl` | `https://eur-lex.europa.eu/eli/C/2026/1944/oj` | Official Journal reference (LOTL signers) |
| `siva.tsl.loader.code` | `EU` | LOTL code in DSS |
| `siva.tsl.loader.lotlRootSchemeInfoUri` | `…/eu-lotl-legalnotice.html` | EU disclaimer |
| `siva.tsl.loader.trustedTerritories` | AT,BE,…,SK,UK (uppercase) | Trusted countries |
| `siva.tsl.loader.schedulerCron` | `0 0 3 * * ?` | Daily 03:00 local refresh |
| `siva.tsl.loader.loadFromCache` | `false` | `true` → offline; cache filenames = URL with special chars → `_` |
| `siva.tsl.loader.onlineCacheExpirationTime` | `PT1H` | Cache TTL (ISO-8601 duration) |
| `siva.tsl.loader.LotlPivotSupportEnabled` | `true` | LOTL pivot mode (since 3.6.0) |
| `siva.tsl.loader.lotlTruststorePath/Type/Password` | `classpath:lotl-truststore.p12` / PKCS12 / `lotl-truststore-password` | LOTL signer keystore |
| `siva.tsl.loader.sslTruststorePath/Type/Password` | `classpath:tsl-ssl-truststore.p12` / PKCS12 / `digidoc4j-password` | TLS truststore for TSL HTTPS |

Relates to [[concepts/trusted-lists]], [[concepts/oj-keystore]], [[concepts/tl-validation-job]], [[concepts/tsl-hot-swap-refresh]].

## ⚠️ EU LOTL signer rotation — April 2026 (time-sensitive)

A new OJ notice was scheduled for **2026-04-14** (Commission Implementing Decision (EU) 2025/2164, OJ C 2026/1944) rotating LOTL signer certificates and **resetting the pivot LOTL chain**. Operators with `LotlPivotSupportEnabled=true` (default since 3.6.0) **must update the LOTL truststore and `siva.tsl.loader.ojUrl` before 2026-04-29**, else EU LOTL + member-state TLs fail to load.

- **< 3.10.0:** override `siva.keystore.filename` / `.password` / `.type` (file location only via env `DSS_DATA_FOLDER`; delete the "temp" truststore).
- **≥ 3.10.0:** override `siva.tsl.loader.lotlTruststorePath` (classpath/file) / `.lotlTruststorePassword` / `.lotlTruststoreType`.
- Set `siva.tsl.loader.ojUrl=https://eur-lex.europa.eu/eli/C/2026/1944/oj`.

Rebuild the truststore with `keytool` from the OJ annex candidates. Pivot mode trusts only the initial set; subsequent pivots chain from it.

## Monitoring endpoints

| Endpoint | Exposure | Note |
|---|---|---|
| `/monitoring/health` | exposed by default | requires nothing |
| `/monitoring/heartbeat` | enabled+exposed by default | requires `health` enabled |
| `/monitoring/version` | enabled+exposed by default | `{version}` |
| `/monitoring/prometheus` | exposed by default | Prometheus exposition |

Toggle via `management.endpoints.web.exposure.include=health,heartbeat,version,prometheus` and `management.endpoint.{heartbeat,version}.enabled=true`.

## Report signing config

`siva.report.reportSignatureEnabled=true` (off by default). `siva.signatureService.{signatureLevel, tspUrl, ocspUrl}` (tspUrl needed ≥ `XAdES_BASELINE_T`; ocspUrl needed ≥ `LT`). Credentials: PKCS#11 `{path,password,slotIndex}` (Estonian smartcard=2, eToken=0) **or** PKCS#12 `{path,password}` — configure one, not both. See [[concepts/siva-report-schema]].

## Statistics

Per validation a JSON statistic is logged at INFO: `stats.{type, sigType, usrId (x-authenticated-user), dur(ms), sigCt, vSigCt, sigRslt[]}` where each `sigRslt` = `{i(indication), si?(subIndication), cc(country from signer cert, XX if unknown), sf(signatureFormat)}`. Redirectable to file/syslog; **not a queue**.

## Roadmap — 3.11.0 (target December 2026)

- **JAdES validation support** (closes the gap vs [[entities/sign-verify-2]] which already supports JAdES).
- Upgrade **DigiDoc4j → 6.2.0**.
- Upgrade **DSS to a 6.x release** (aligns the fork closer to upstream; see [[entities/dss]]).
- General dependency / maintenance updates.

Past releases: GitHub `open-eid/SiVa/releases`.

## See also

[[entities/siva]] · [[concepts/siva-rest-interface]] · [[concepts/siva-validation-policy]] · [[concepts/trusted-lists]] · [[concepts/oj-keystore]] · [[concepts/tl-validation-job]] · [[concepts/tsl-hot-swap-refresh]] · [[analyses/siva-vs-sign-verify-2]]
