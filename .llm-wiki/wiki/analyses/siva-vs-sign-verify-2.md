---
type: analysis
created: 2026-06-28
updated: 2026-06-28
query: "confronto open-eid/SiVa vs sign-verify-2 e punti di miglioramento"
sources:
  - sources/siva-research
  - sources/SRC-2026-06-27-002
  - sources/SRC-2026-06-27-008
---

# SiVa vs sign-verify-2 — confronto e punti di miglioramento

Confronto tra [[entities/siva|open-eid/SiVa]] (servizio di validazione firme dell'agenzia estone RIA) e [[entities/sign-verify-2]]. Entrambi sono **solo-validazione** eIDAS basati su EU DSS, quindi SiVa è il peer pubblico più vicino. Obiettivo: capire cosa SiVa fa meglio e ricavarne un backlog di miglioramenti.

## Quadro sintetico

| Dimensione | SiVa | sign-verify-2 |
|---|---|---|
| Libreria DSS | **fork** `org.digidoc4j.dss` (lag dietro upstream) | **upstream DSS 6.4** ✅ |
| Architettura | monolite modulare (gateway→proxy→validatori per formato) | esagonale, porte/adapter, ArchUnit ✅ |
| API | REST sync, **no async/callback/batch** | REST **+ job async + webhook HMAC** ✅ |
| Auth | delegata a X-Road | API-key + OAuth2 JWT ✅ |
| Report | Simple/Detailed/Diagnostic, **arricchito** | piatto (`indication/subIndication/format/count`) ❌ |
| Livello qualifica (QES/AdES) | **`signatureLevel` enum esposto** | non esposto ❌ |
| Marche temporali nel report | `timeStampTokens[]`, `archiveTimeStamps[]`, livello QTSA/TSA | non strutturato ❌ |
| Report firmato (non-ripudio) | **sì, ASiC-E firmato (PKCS#11/12)** | no ❌ |
| Hashcode mode (validazione per hash) | **sì** | no ❌ |
| Policy | POLv3 (AdES-permissiva) / POLv4 (QES-only, default) | preset BASIC/STANDARD/STRICT |
| Policy custom integratore | no | no (entrambi limitati) |
| Trusted Lists | EU LOTL (ETSI) + liste statiche SK per DDOC | EU LOTL, [[concepts/tsl-hot-swap-refresh|hot-swap]] ShedLock ✅ |
| Monitoring | `/monitoring/{health,heartbeat,version,prometheus}` | Actuator + Prometheus (≈parità) |
| Statistiche | per-validazione (syslog-JSON / GA) | `AuditService` **non ancora cablato** ❌ |
| Test corpus | ampio (DDOC/BDOC/ASiC/PAdES B/T/LT/LTA, ≤9 MB) + Gatling | limitato (in crescita) ❌ |
| Formati extra | DDOC/BDOC (legacy estone) — irrilevanti per PA IT | — |

**Dove sign-verify-2 è già avanti:** upstream DSS (niente fork-lag), async+webhook, auth applicativa (API-key/OAuth), architettura esagonale testata da ArchUnit, TSL hot-swap, `problem+json` (RFC 9457), circuit breaker/backpressure. Questi sono inversioni deliberate dei limiti principali di SiVa e **vanno mantenute come punti di forza**.

## Punti di miglioramento (da SiVa)

Ordinati per valore/sforzo. Dettaglio operativo in [[../outputs/improvement-points-from-siva-2026-06-28]].

1. **Report arricchito `signatures[]`/`timestamps[]`** — SiVa espone per-firma `signedBy`, `claimedSigningTime`, `bestSignatureTime`, `signatureLevel`, e array separati `timeStampTokens[]`/`archiveTimeStamps[]` con livello QTSA/TSA e `certificates[]` tipizzati. Conferma e dà forma alla **Phase 4** del piano [[../outputs/plan-verifica-file-tsd-2026-06-28|TSD]]. Vedi [[analyses/tsd-dto-mapping]]. *(alto valore)*
2. **Livello di qualifica eIDAS** — esporre `signatureLevel` (QESIG/QESEAL/ADESIG_QC/…). DSS lo fornisce (`SignatureQualification`/`SimpleReport.getSignatureQualification`); oggi sign-verify-2 non lo mappa. *(alto valore, basso sforzo)*
3. **Report di validazione firmato** — SiVa restituisce il Detailed report in un container **ASiC-E firmato** (PKCS#11/12) → non-ripudio del verdetto. Differenziatore forte per una PA. Nuovo port `ReportSignerPort` + adapter DSS. *(alto valore, medio sforzo)*
4. **Hashcode validation** — validare per `hashAlgo`+`hash` senza il file originale (privacy/banda). DSS supporta `DigestDocument`; la logica è simile al resolver imprint già scritto per il [[concepts/rfc5544-tsd|TSD]]. Nuovo endpoint o variante di `/verifications`. *(medio valore)*
5. **Health indicator dedicati** — oltre Actuator, aggiungere health di **freschezza TSL** e disponibilità DSS, più build/version info (SiVa ha `/monitoring/{health,heartbeat,version}`). Collega [[concepts/tsl-hot-swap-refresh]]. *(basso sforzo)*
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
