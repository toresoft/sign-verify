---
type: source
created: 2026-06-28
title: "open-eid/SiVa — architecture, formats, policy, report schema, ops (research 2026-06-28)"
url: https://open-eid.github.io/SiVa/
tags: [siva, eidas, dss, validation, comparison]
confidence: high
volatility: warm
---

# Source: open-eid/SiVa research (2026-06-28)

Consolidated provenance for the SiVa comparison. Multi-agent web research over first-party RIA/open-eid documentation (SiVa is almost entirely first-party documented; no significant independent critiques found).

> **Primary raw backing:** the full SiVa documentation corpus (60 files, commit `348a6b2`, EUPL-1.1) is captured immutably in raw packet [[sources/SRC-2026-06-28-001]]. The deep-dive concept pages — [[concepts/siva-rest-interface]], [[concepts/siva-validation-policy]], [[concepts/siva-report-schema]], [[concepts/siva-deployment-ops]] — are synthesized from that packet and supersede the URL-level summary below where they go deeper.

## Primary references
- GitHub repo — https://github.com/open-eid/SiVa (Java 17, Spring Boot, Maven wrapper, EUPL-1.1, 3.10.0 May 2026).
- Component model — https://open-eid.github.io/SiVa/siva3/structure_and_activities/ (gateway → proxy → Generic/Timemark/TST/Hashcode validators; report-signing, statistics, TSL loader).
- Interfaces / report schema — https://open-eid.github.io/SiVa/siva3/interfaces/ (`/validate`, `/validateHashcode`, `/getDataFiles`; Simple/Detailed/Diagnostic; signatureLevel enum; timeStampTokens[]; monitoring endpoints).
- Validation policy — https://open-eid.github.io/SiVa/siva3/appendix/validation_policy/ (POLv3 type-agnostic vs POLv4 qualified-only default; OCSP/CRL freshness; TL trust anchors).
- Overview / format↔library map — https://open-eid.github.io/SiVa/siva/overview/.
- Deployment — https://open-eid.github.io/SiVa/siva3/deployment_guide/ (fat JAR 8080 / WAR-per-Tomcat; stateless; 2–4 GB RAM).
- Test plan / QA — https://open-eid.github.io/SiVa/siva3/test_plan/ + /qa_strategy/ (open-eid/Siva-test RestAssured corpus; Gatling perftests; GitHub Actions).
- DSS fork coordinates — https://mvnrepository.com/artifact/org.digidoc4j.dss/specs-trusted-list (`org.digidoc4j.dss`, fork of upstream `eu.europa.ec.joinup.sd-dss`).
- Public demo — https://siva-demo.eesti.ee/V3/.

## Key facts captured
- Validation-only, sync-only (no async/callbacks/batch), REST/JSON (SOAP dropped in 3.10).
- Per-format engine routing over a **DigiDoc4J DSS fork** (maintenance-lag/coupling risk) — opposite of sign-verify-2's upstream DSS 6.4.
- Value-adds over a plain DSS wrapper: signed ASiC-E report, qualification `signatureLevel` enum, hashcode mode, DDOC datafile extraction, signed-report non-repudiation.
- Closed policy set (POLv3/POLv4), Estonian-legislation carve-outs baked in; no integrator-supplied constraint XML.
