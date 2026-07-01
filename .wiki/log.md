# Activity Log

## 2026-06-30 ll | "GitLab/GitHub CI release + vuln-scan pipeline hardening" → raw/notes/2026-06-30-ll-ci-release-pipeline-hardening.md (5 lessons, 0 articles updated)
## 2026-06-28 ingest | Quadro normativo firma digitale: eIDAS, CAD, DPCM 2013, ETSI, IETF (raw/notes/2026-06-28-quadro-normativo-firma-digitale-eidas.md)
## 2026-06-28 research | "European regulatory references for digital signatures" → 5 sources ingested, 4 articles compiled/updated
## 2026-06-30 lint --fix | 18 checks, 0 critical, 0 warnings remaining, 0 candidates, auto-fixed: 8 orphan articles registered, 6 broken slug links repaired, 78 volatility:warm added
## 2026-06-30 lint | 18 checks, 0 critical, 2 warnings (registry drift: 8 wiki articles unregistered; ~6 broken slug links), 2 info (78 missing volatility, source-type sources), 0 candidates, 0 auto-fixed (report-only)
## 2026-06-30 compile | 4 sources (SRC-2026-06-29-001..004) → 1 new article (concepts/firma-con-spid), 1 updated (concepts/italian-digital-signature-law); ll + openapi notes already integrated
## 2026-06-29 ingest | AgID PDFs/HTML full packets → SRC-2026-06-29-001..004 (Linee Guida art.20, CNIPA 45/2009, cap. crittografia, QES page)
## 2026-06-29 ingest | AgID signature/crypto rules (Det.157/2020, CNIPA 45/2009, cap. crittografia) → sources/agid-signature-rules-research.md
## 2026-06-29 ll | "authoring DSS validation policies (AGID presets)" → raw/notes/2026-06-29-ll-dss-policy-authoring.md (6 lessons, 1 article updated: concepts/dss-policy-xml)
## 2026-06-29 research | AgID Regole Tecniche crittografia (SHA256/RSA2048/ECDSA-P256) + Det.157/2020 → hardened AGID/AGID_TS to QES-strict (QcSSCD, TrustService CA/QC, country IT)
## 2026-06-29 update | AGID/AGID_TS presets → concepts/validation-profiles, concepts/italian-digital-signature-law
## 2026-06-29 compile | 3 sources → 3 new articles (sources/openapi-spec-sign-verify-2, concepts/italian-digital-signature-law, sources/ll-tsd-jackson-toolchain); 3 updated (entities/sign-verify-2, entities/eidas-regulation, concepts/rfc5544-tsd)
## 2026-06-29 ingest | sign-verify-2 OpenAPI spec (openapi.yaml) (raw/notes/2026-06-29-openapi-spec-sign-verify-2.md)
## 2026-06-29 query | "è possibile sapere usando le api di verifica firma che si abbia una firma invece che un sigillo?" → answered from 4 articles (standard)
## 2026-06-30 ll | "running DSS validation tests offline (717s->40s)" -> raw/notes/2026-06-30-ll-dss-offline-test-verifier.md (5 lessons, 1 article updated: entities/certificateverifier)
## 2026-07-01 ll | "recursive container unwrap + optional filename in /extractions" -> raw/notes/2026-07-01-ll-extraction-recursive-unwrap.md (4 lessons, 2 articles updated: concepts/circuit-breaker, concepts/file-extraction)
## 2026-07-01 research | "differenze estrazione file originale sign-verify-2 vs SiVa" -> 1 analysis compiled (analyses/extraction-siva-vs-sign-verify-2), 2 web sources confirmed, 1 article cross-linked (analyses/siva-vs-sign-verify-2)
## 2026-07-01 research | "differenze verifica firma sign-verify-2 vs SiVa" -> 1 analysis compiled (analyses/verification-siva-vs-sign-verify-2), 2 web sources confirmed (POLv3/v4, hashcode+signed report), cross-linked
## 2026-07-01 research | "differenze architettura/pattern verifica firma sign-verify-2 vs SiVa" -> 1 analysis compiled (analyses/architecture-siva-vs-sign-verify-2), 2 web sources confirmed (component model, deployment), cross-linked
## 2026-07-01 correction | architecture-siva-vs-sign-verify-2: TSD RFC5544 e GIA un formato fuori DSS -> integrato via Decorator+try-fallback (BouncyCastle in-process), non proxy/selector; corretta la sezione "cosa imparare"
## 2026-07-01 compile | 1 source (ll-extraction-recursive-unwrap) -> 1 new article (entities/recursiveextractionadapter), 4 updated (entities/tsdawareextractionadapter[renamed-pointer], entities/dssextractionadapter, concepts/problemjson, concepts/rfc5544-tsd)
