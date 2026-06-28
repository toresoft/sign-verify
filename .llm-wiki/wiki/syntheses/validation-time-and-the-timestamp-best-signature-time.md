---
type: synthesis
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-001
  - sources/SRC-2026-06-27-002
---

# Validation time and the timestamp (best-signature-time)

_Synthesis answering: «attualmente la validazione avviene al tempo della marca temporale se esiste?»_

## Risposta breve
**Sì, ma automaticamente e a livelli — non è una scelta esplicita di sign-verify-2.** [[entities/sign-verify-2]] non imposta né un `ValidationLevel` né un tempo di validazione, quindi [[entities/dss]] esegue il **livello più alto (con LTA)**. Quel processo include, per le firme "with-time / LT", la validazione **contro il best-signature-time = tempo di produzione della marca temporale della firma** (docs §20.2.4). La validazione **base** parte comunque al **tempo corrente**; la validazione "nel passato" via POE si attiva solo se la base al current-time è `INDETERMINATE` e i timestamp possono risolverla.

## Comportamento corrente (dal codice)
`DssValidatorAdapter.validate()` ([[entities/dss-validator-adapter]]) fa esattamente:
```java
validator = SignedDocumentValidator.fromDocument(doc);
validator.setCertificateVerifier(certificateVerifier);
policy = new EtsiValidationPolicyFactory().loadValidationPolicy(policyDoc);
Reports reports = validator.validateDocument(policy);
```
- **Non** chiama `setValidationLevel(...)` → default DSS = livello più alto (LTA).
- **Non** imposta un current-time/validation-date.
- Aggrega `worstSignatureId(...)` → esito top-level = firma peggiore ([[concepts/signature-validation]]).

## Cosa significa, per livelli (ETSI EN 319 102-1, §20.2.4)
| Livello | Tempo di valutazione |
|---|---|
| Basic signature | **tempo corrente** (validation time) |
| Signatures with Time / LT | **best-signature-time = tempo di produzione della marca temporale** della firma |
| LTA | tutto il materiale long-term, incluse archive timestamp (POE scalanti) |

Past-validation (PCV/VTS/POE-extraction/PSV) viene attivata quando la validazione base al current-time è `INDETERMINATE` e ci sono POE utilizzabili: il **best-signature-time** si inizializza da un POE correlato o, in mancanza, dal current-time.

## Conseguenze operative
1. **Per un `.tsd`/.p7m marcato temporalmente** (CAdES-T/LT, vedi [[analyses/verifica-file-tsd]]): la validazione "with-time" usa il tempo della marca → la firma resta valida anche **dopo** la scadenza/revoca del certificato di firma, purché la marca sia precedente a quell'evento (è il valore del livello T). DSS lo ottiene **senza** che sign-verify-2 faccia nulla di esplicito.
2. **Il profilo può restringere** i livelli valutati: il `Level`/i blocchi del [[concepts/dss-policy-xml|policy XML]] (basic / long-term / archival, vedi [[sources/SRC-2026-06-27-003]] §4.2) selezionano quali constraint girano → un profilo "basic-only" valuterrebbe **solo al current time**. Quindi "valida al tempo della marca" dipende anche dal profilo scelto, non solo dal default.
3. **`verifiedAt`** nella risposta = `now`, non il best-signature-time. Il best-signature-time è interno a DSS e visibile nel [[concepts/reports|DetailedReport]], non nel top-level. Gap da considerare se si vuole esporre "fino a quando la firma risulta valida".

## Riferimenti
- DSS docs §20.2.4 (AdES validation levels & tempi), §7 (past validation / POE) — [[sources/SRC-2026-06-27-001]]
- Codice: `DssValidatorAdapter.validate` — `src/main/java/.../adapter/dss/DssValidatorAdapter.java`
- Design spec §6 (engine) — [[sources/SRC-2026-06-27-002]]
- Completa la sintesi [[syntheses/validation-at-a-specific-date-control-time]] (variante "forzare una data")

## Related
- [[concepts/signature-validation]] · [[concepts/baseline-profiles]] · [[concepts/timestamping]]
- [[entities/dss-validator-adapter]] · [[entities/dss]] · [[entities/signeddocumentvalidator]]
- [[concepts/validation-profiles]] · [[concepts/dss-policy-xml]] · [[analyses/verifica-file-tsd]]
- [[syntheses/validation-at-a-specific-date-control-time]]