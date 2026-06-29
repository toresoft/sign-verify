---
title: "Regolamento UE 910/2014 (eIDAS) — disposizioni su firme elettroniche"
source: "https://eur-lex.europa.eu/eli/reg/2014/910/oj/eng"
type: articles
ingested: 2026-06-28
tags:
  - eIDAS
  - Regolamento-910-2014
  - firma-elettronica-qualificata
  - FEQ
  - Art-25
  - Art-32
  - Art-42
  - riconoscimento-reciproco
  - validazione
  - trust-services
summary: >
  Analisi del Regolamento UE 910/2014 (eIDAS) per le firme elettroniche.
  Copre: Art. 25 (effetti legali e riconoscimento reciproco FEQ),
  Art. 26 (requisiti FEA), Art. 32 (validazione FEQ), Art. 42 (marche
  temporali qualificate), Art. 22 (Trusted Lists).
  Include riferimenti a eIDAS 2.0 (Reg. 2024/1183) e termination plan.
---

# Regolamento UE 910/2014 (eIDAS) — disposizioni su firme elettroniche

## Quadro normativo

Il Regolamento (UE) n. 910/2014 (eIDAS) stabilisce il quadro giuridico per
l'identificazione elettronica e i servizi fiduciari (trust services) nel
mercato interno UE. Ha sostituito la Direttiva 1999/93/CE sulle firme
elettroniche, passando da un approccio di direttiva (recepita diversamente
da ogni Stato) a un regolamento direttamente applicabile in tutti gli
Stati membri.

## Art. 25 — Effetti legali delle firme elettroniche

- **Comma 1**: Una firma elettronica non può essere negata come prova in
  sede legale solo perché in forma elettronica o perché non è una FEQ.
- **Comma 2**: Una FEQ ha lo stesso effetto legale di una firma autografa.
- **Comma 3**: Una FEQ basata su un certificato qualificato emesso in uno
  Stato membro è riconosciuta come FEQ in tutti gli altri Stati membri
  (riconoscimento reciproco obbligatorio, senza bisogno di ulteriori
  formalità).

## Art. 26 — Requisiti della FEA

La firma elettronica avanzata (FEA) deve:
(a) essere connessa unicamente al firmatario;
(b) consentire l'identificazione del firmatario;
(c) essere creata con dati per la creazione della firma che il firmatario
    può utilizzare con un elevato livello di controllo esclusivo;
(d) essere collegata ai dati firmati in modo da consentire l'individuazione
    di ogni successiva modifica.

## Art. 32 — Requisiti per la validazione delle FEQ

Il processo di validazione deve confermare che:
(a) il certificato era, al momento della firma, un certificato qualificato
    conforme all'Allegato I;
(b) il certificato è stato emesso da un QTSP ed era valido al momento
    della firma;
(c) i dati di validazione corrispondono ai dati forniti al relying party;
(d) il set unico di dati che rappresenta il firmatario è correttamente
    fornito al relying party;
(e) l'uso di pseudonimo è chiaramente indicato al relying party;
(f) la firma è stata creata con un SSCD (dispositivo sicuro di creazione);
(g) l'integrità dei dati firmati non è stata compromessa;
(h) i requisiti dell'Art. 26 erano soddisfatti al momento della firma.

Il sistema di validazione deve fornire il risultato corretto e consentire
al relying party di rilevare problemi di sicurezza.

## Art. 33 — Servizio di validazione qualificato

Può essere fornito solo da un QTSP che:
(a) esegue la validazione in conformità all'Art. 32(1);
(b) fornisce il risultato in modo automatizzato, affidabile, efficiente e
    munito di firma elettronica avanzata o sigillo elettronico avanzato
    del QTSP.

## Art. 42 — Marca temporale qualificata

Requisiti:
(a) lega data/ora ai dati in modo da precludere modifiche non rilevabili;
(b) basata su una fonte temporale accurata collegata a UTC;
(c) firmata con firma elettronica avanzata o sigillo elettronico avanzato
    del QTSP.

**Art. 41 (presunzione)**: Le marche temporali qualificate godono della
presunzione di accuratezza della data/ora e dell'integrità dei dati.

## Art. 22 — Trusted List

Ogni Stato membro pubblica e mantiene una Trusted List nazionale dei QTSP
e dei servizi fiduciari qualificati. La Commissione pubblica la LOTL (List
of Trusted Lists) che contiene i puntatori a tutte le TL nazionali.

## eIDAS 2.0 — Regolamento (UE) 2024/1183

Modifica il Reg. 910/2014 introducendo:
- European Digital Identity Wallet (EUDI Wallet)
- Obbligo per i QTSP di stabilire un **termination plan** per ogni servizio
  qualificato (copre sia cessazione anticipata che imprevista, come
  fallimento o vendita)
- Revisione obbligatoria del termination plan almeno ogni 2 anni
- Il termination plan deve specificare procedure per: continuità del
  servizio, accessibilità delle prove in sede legale, nessun impatto
  negativo su validità delle emissioni pregresse

## Regolamento (UE) 2025/1929 — Binding date/time e accuratezza fonti

Stabilisce gli standard di riferimento per le marche temporali qualificate
ai sensi dell'Art. 42(2). Riferimento a ETSI EN 319 421 e 319 422.
Il qcStatement "esi4-qtstStatement-1" nel time-stamp token indica che è
emesso come marca temporale qualificata.
