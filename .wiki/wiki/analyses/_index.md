
- [[extraction-siva-vs-sign-verify-2]] — Estrazione file originale: SiVa /getDataFiles (DDOC-only, JDigiDoc, single-level) vs sign-verify-2 /extractions (qualsiasi container DSS + TSD, ricorsivo, filename opzionale)

- [[verification-siva-vs-sign-verify-2]] — Verifica firma: SiVa (fork DSS, sync-only, POLv3/POLv4, hashcode + report firmato ASiC-E, X-Road) vs sign-verify-2 (DSS 6.4 upstream, async+webhook, preset+override, RFC 5544 TSD, problem+json, circuit breaker)

- [[architecture-siva-vs-sign-verify-2]] — Architettura/pattern verifica: SiVa (monolite modulare multi-webapp, proxy-selector per documentType, un Tomcat per servizio per JAR-hell multi-libreria) vs sign-verify-2 (esagonale ports/adapter + ArchUnit, dispatch via factory DSS, decorator TSD, singolo motore DSS upstream)
