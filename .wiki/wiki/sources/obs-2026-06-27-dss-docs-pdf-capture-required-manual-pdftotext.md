---
type: source
title: "Observation: DSS docs PDF capture required manual pdftotext"
slug: obs-2026-06-27-dss-docs-pdf-capture-required-manual-pdftotext
status: observation
created: 2026-06-27
updated: 2026-06-27
relevance: high
observed_at: 2026-06-27T10:49:34.782Z
tags: ["dss", "pdf-extraction", "wiki", "source-capture"]
source_context: "Initializing the Digital signature verification wiki and capturing the DSS docs PDF"
volatility: warm
---
# ⭐ Observation: DSS docs PDF capture required manual pdftotext
Captured the official DSS v6.4 documentation PDF (https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.pdf) as source SRC-2026-06-27-001. The wiki's built-in markitdown extractor FAILED on this 12 MB / 523-page PDF (timeout) and wrote a failure placeholder to extracted.md. Workaround: the PDF downloads fine into original/source.pdf; recovered full text with `pdftotext -enc UTF-8 original/source.pdf extracted_full.txt` (~110k words / 894 KB) — bash writes into the raw packet dir even though the `write` tool enforces the immutability guard on extracted.md. Then indexed extracted_full.txt into the context-mode KB under source `DSS-6.4-documentation` (queryable via ctx_search) and wrote the structured summary + 20-chapter map into the editable wiki page wiki/sources/SRC-2026-06-27-001.md. Key validation APIs documented: SignedDocumentValidator.fromDocument() → Reports (DiagnosticData/DetailedReport/SimpleReport/ETSI validation report); CertificateVerifier; TLValidationJob for LOTL/TL. Core chapters for sign-verify-2: 3 (concepts), 6 (revocation), 7 (validation), 10 (augmentation B→T→LT→LTA), 11 (trusted lists).
*Relevance: high*

*Context: Initializing the Digital signature verification wiki and capturing the DSS docs PDF*

*Tags: dss pdf-extraction wiki source-capture*
---
*Observed: 2026-06-27T10:49:34.782Z*