---
type: source
title: ".tsd (PA) = CAdES firma marcatemporalmente, non timestamp detached"
slug: tsd-is-cades-walk-not-detached-timestamp
status: insight
created: 2026-06-27
updated: 2026-06-27
category: correction
---
# .tsd (PA) = CAdES firma marcatemporalmente, non timestamp detached
Correzione di un'analisi di wiki: avevo inizialmente trattato `.tsd` come contenitore "Time-Stamped Data" *timestamp-only* → `DetachedTimestampValidator`. Sbagliato per il caso operativo reale. Nella PA italiana (ArubaSign, GoSign) un `.tsd` è un **inviluppo CAdES/CMS con firma(e) + marca(e) temporale(i) embedded** (CAdES-BASELINE-T / LT/LTA), **identico a un `.p7m`**. Validarlo = **navigare tutti i contenitori/signature/contofirme annidate** — comportamento che `SignedDocumentValidator.fromDocument()` + `validateDocument()` già fornisce (DSS docs: `getSignatures()` → `List<AdvancedSignature>` con multi/nested/counter signatures; timestamp embedded gestiti a livello T/LT/LTA). Implicazione per [[analyses/verifica-file-tsd|sign-verify-2]]: **non serve un ramo dedicato** — l'autodetect CAdES già copre il caso. Il lavoro reale è nella **forma del risultato** (esporre ogni firma + ogni marca temporale embedded nel report DTO, semantica di aggregazione delle indication tra firma B-level e marca TSA). `DetachedTimestampValidator` va declassato: serve **solo per timestamp nudo** (`.tsr` / CMS `TimeStampToken` senza SignedData firmata). Discriminatore al bordo: `DetachedTimestampAnalyzer`, mai basato sull'estensione (DSS rileva per *contenuto*, §5). Lezione metodologica: con formati PA italiani, **verificare la semantica operativa** (come vengono effettivamente prodotti/usati) prima di mappare su un'API — ho over-fit su "Time-Stamped" nel nome. Confronto utile: `.tsd`≅`.p7m` CAdES-T (autocontenuto), `.tsr` = token detached puro.
*Category: correction*
---
*Captured: 2026-06-27*
## Related
_Add links to related pages._