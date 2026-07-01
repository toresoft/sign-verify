# Raw Notes

- [[2026-06-28-ll-tsd-jackson-toolchain]] — Lessons: RFC 5544 imprint resolution, DSS↔BC routing, Jackson CVE pinning, JDK-25 Spotless/JaCoCo gotchas (5 lessons)
- [[2026-06-28-quadro-normativo-firma-digitale-eidas]] — Quadro normativo firma digitale: eIDAS, CAD, DPCM 2013, ETSI, IETF
- [[2026-06-29-ll-dss-policy-authoring]] — Lessons: DSS policy XML authoring — TLevelTimeStamp, XSD validation, no -- in comments, duplicated SigningCertificate, TSA EKU, AlgoExpirationDate (6 lessons)
- [[2026-06-29-openapi-spec-sign-verify-2]] — sign-verify-2 OpenAPI contract: endpoint, schema, signatureLevel
- [[2026-06-30-ll-dss-offline-test-verifier]] — Lessons: DSS tests offline 717s→40s — AIA (not OCSP/CRL) was the cost, diagnose-before-rerun, mvn -q hides surefire, global @Primary verifier override, @Tag on both surefire+failsafe (5 lessons)
- [[2026-06-30-ll-ci-release-pipeline-hardening]] — Lessons: GitLab/GitHub release pipelines — run_id cache keys never reused, GitLab job cache: overrides (not merges) global cache, dependency-check non-JVM analyzers, release assets must outlive job-artifact expiry, Trivy table format needs explicit output file (5 lessons)
- [[2026-07-01-ll-extraction-recursive-unwrap]] — Lessons: recursive container unwrap + optional filename — recursion re-feeds internals so unguarded DSS calls poison the circuit breaker, BC TSD needs valid temporalEvidence, AppException.getMessage()==title (assert getDetail()), MAX_DEPTH + depth-aware leaf-vs-error termination (4 lessons)
