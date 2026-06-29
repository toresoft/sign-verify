---
type: analysis
created: 2026-06-28
updated: 2026-06-28
query: "confronto open-eid/SiVa vs sign-verify-2 e punti di miglioramento"
sources:
  - sources/SRC-2026-06-28-001
  - sources/siva-research
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-008
volatility: warm
---

# SiVa vs sign-verify-2 — confronto e punti di miglioramento

Confronto tra [[entities/siva|open-eid/SiVa]] (servizio di validazione firme dell'agenzia estone RIA) e [[entities/sign-verify-2]]. Entrambi sono **solo-validazione** eIDAS basati su EU DSS, quindi SiVa è il peer pubblico più vicino. Obiettivo: capire cosa SiVa fa meglio e ricavarne un backlog di miglioramenti.

## Quadro sintetico

| Dimensione | SiVa | sign-verify-2 |
|---|---|---|
| Libreria DSS | **fork** `org.digidoc4j.dss` (lag upstream; **3.11 Dic 2026 pianifica upgrade a DSS 6.x** → gap in riduzione) | **upstream DSS 6.4** ✅ |
| Architettura | monolite modulare (gateway→proxy→validatori per formato) | esagonale, porte/adapter, ArchUnit ✅ |
| API | REST sync, **no async/callback/batch** | REST **+ job async + webhook HMAC** ✅ |
| Auth | delegata a X-Road | API-key + OAuth2 JWT ✅ |
| Report | Simple/Detailed/Diagnostic, **arricchito** + **signed ASiC-E** | simple/detailed/diagnostic/**etsi** ([[concepts/reports]]) — il gap reale è `signatureLevel` + `timeStampTokens[]` strutturati + report firmato, **non** "piatto" ⚠️ |
| Livello qualifica (QES/AdES) | **`signatureLevel` enum esposto** | **esposto** ✅ (`SignatureSummary.signatureLevel` ← DSS `getSignatureQualification`, vedi commit `dd9878f`) |
| Marche temporali nel report | `timeStampTokens[]`, `archiveTimeStamps[]`, livello QTSA/TSA | non strutturato nel DTO ❌ |
| Report firmato (non-ripudio) | **sì, ASiC-E firmato (PKCS#11/12)** | no ❌ |
| Hashcode mode (validazione per hash) | **sì** | no ❌ |
| Policy | POLv3 (AdES-permissiva) / POLv4 (QES-only, default) | preset BASIC/STANDARD/STRICT |
| Policy custom integratore | no | no (entrambi limitati) |
| Trusted Lists | EU LOTL con **pivot support** (default da 3.6.0), refresh cron `0 0 3 * * ?`, cache online/offline, T-level revocation filter per-paese; liste statiche SK **solo per DDOC** | EU LOTL, [[concepts/tsl-hot-swap-refresh|hot-swap]] ShedLock ✅ (edge: swap senza restart) |
| Monitoring | `/monitoring/{health,heartbeat,version,prometheus}` | Actuator + Prometheus (≈parità) |
| Statistiche | per-validazione (syslog-JSON / GA) | `AuditService` **non ancora cablato** ❌ |
| Test corpus | ampio (DDOC/BDOC/ASiC/PAdES B/T/LT/LTA, ≤9 MB) + Gatling | limitato (in crescita) ❌ |
| Formati extra | DDOC/BDOC (legacy estone) — irrilevanti per PA IT; **JAdES in arrivo nel 3.11 (Dic 2026)** | **JAdES già supportato** ✅ (gap temporaneo) |

> **Verificato il 2026-06-28** contro i raw docs [[sources/SRC-2026-06-28-001]] (commit `348a6b2`, 60 file SiVa). Correzioni applicate alla tabella: (1) riga *Report* — sign-verify-2 espone `simple`/`detailed`/`diagnostic`/`etsi` per [[concepts/reports]], non "piatto"; il gap reale è `signatureLevel` + timestamp strutturati + report firmato; (2) riga *DSS* — SiVa 3.11 (Dic 2026) pianifica upgrade a DSS 6.x (`roadmap.md`); (3) riga *Trusted Lists* — SiVa ha pivot LOTL + cron refresh + cache + T-level revocation filter, non solo liste statiche; (4) riga *Formati* — SiVa aggiunge JAdES nel 3.11. Dettagli: [[concepts/siva-validation-policy]], [[concepts/siva-rest-interface]], [[concepts/siva-report-schema]], [[concepts/siva-deployment-ops]].

**Dove sign-verify-2 è già avanti:** upstream DSS (niente fork-lag), async+webhook, auth applicativa (API-key/OAuth), architettura esagonale testata da ArchUnit, TSL hot-swap, `problem+json` (RFC 9457), circuit breaker/backpressure. Questi sono inversioni deliberate dei limiti principali di SiVa e **vanno mantenute come punti di forza**.

## Punti di miglioramento (da SiVa)

Ordinati per valore/sforzo. Dettaglio operativo in [[../outputs/improvement-points-from-siva-2026-06-28]].

1. **Report arricchito — residuo** — `signatures[]` espone già `signedBy`, `bestSignatureTime`, `signatureFormat`, `signatureLevel`, `indication`/`subIndication` e `signatures[].timestamps[]` con livello QTSA/TSA (commit `dd9878f`/`6912fc5`). Manca ancora vs SiVa: `claimedSigningTime`, l'array separato `archiveTimeStamps[]`, e `certificates[]` tipizzati per firma. Dà forma alla **Phase 4** del piano [[../outputs/plan-verifica-file-tsd-2026-06-28|TSD]]. Vedi [[analyses/tsd-dto-mapping]]. *(medio valore)*
2. ~~**Livello di qualifica eIDAS** — esporre `signatureLevel`~~ ✅ **Fatto** (commit `dd9878f`): `SignatureSummary.signatureLevel` ← DSS `SimpleReport.getSignatureQualification(id).name()`, testato in `DssValidatorAdapterTest#enriches_response_with_signatures_and_qualification`.
3. **Report di validazione firmato** — SiVa restituisce il Detailed report in un container **ASiC-E firmato** (PKCS#11/12) → non-ripudio del verdetto. Differenziatore forte per una PA. Nuovo port `ReportSignerPort` + adapter DSS. *(alto valore, medio sforzo)*
4. **Hashcode validation** — validare per `hashAlgo`+`hash` senza il file originale (privacy/banda). DSS supporta `DigestDocument`; la logica è simile al resolver imprint già scritto per il [[concepts/rfc5544-tsd|TSD]]. Nuovo endpoint o variante di `/verifications`. *(medio valore)*
5. ~~**Health indicator dedicati** — freschezza TSL + disponibilità DSS + build/version info~~ ✅ **Fatto** (commit `4b4c262` + preesistenti): `TslReadinessIndicator` (lastRefreshStatus/At, cert count, gating readiness OUT_OF_SERVICE), `DssHealthIndicator` (CertificateVerifier wired), `JobQueueHealthIndicator`, e `/actuator/info` con `BuildProperties`+`GitProperties` (`build-info`/`git-properties` plugin). `show-details: when-authorized` (PRIVILEGED). Test: `DssHealthIndicatorTest`, `TslReadinessIndicatorTest`, `JobQueueHealthIndicatorTest` (4/4 verdi).
6. **Corpus di conformità reale + perf** — replicare l'approccio `Siva-test`: corpus di documenti firmati reali (PAdES/CAdES/XAdES/ASiC, profili B/T/LT/LTA, multi-firma, validi+invalidi) e load test (Gatling/k6). Si aggancia a [[analyses/cades-pades-test-corpus]] e [[analyses/tsd-test-corpus]]. *(medio sforzo, alto valore di fiducia)*
7. **Cablare audit + statistiche d'uso** — chiudere il gap noto (`AuditService` non wired) ed emettere statistiche per-validazione (tipo formato, esito) come SiVa. *(medio)*
8. **Semantica policy QES-only vs AdES** — documentare/allineare i preset alla distinzione eIDAS (come POLv4 default QES-only vs POLv3 permissiva), rendendola esplicita nella descrizione OpenAPI del campo `indication`/policy. *(basso sforzo)*
9. **Validazione timestamp-token dedicata** — SiVa ha un *TST Validation Service* per ASiC-S/token nudi. sign-verify-2 ha appena aggiunto il TSD RFC 5544; valutare un endpoint/strato dedicato per `.tsr`/ASiC-S come già suggerito in [[analyses/verifica-file-tsd]]. *(medio)*

## Anti-pattern di SiVa da NON copiare
- **Fork DSS** (`org.digidoc4j.dss`): coupling e ritardo sugli aggiornamenti upstream (es. transizione Trusted List v6). sign-verify-2 fa bene a restare su upstream DSS 6.4.
- **Sync-only**: l'async + webhook di sign-verify-2 è superiore per carichi batch.
- **Un Tomcat per servizio** per conflitti di libreria: conseguenza del multi-libreria; l'architettura a singolo servizio esagonale evita il problema.
- **Trust estone hardcoded** per DDOC: non neutrale eIDAS; non replicabile né desiderabile in contesto PA IT.

## Related
- [[entities/siva]] · [[entities/sign-verify-2]] · [[entities/dss]]
- [[analyses/tsd-dto-mapping]] · [[analyses/verifica-file-tsd]] · [[concepts/reports]]
- [[concepts/etsi-en-319-102-1-validation]] · [[concepts/validation-profiles]] · [[concepts/trusted-lists]]
- [[../outputs/improvement-points-from-siva-2026-06-28]]
