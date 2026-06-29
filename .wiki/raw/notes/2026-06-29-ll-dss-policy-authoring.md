---
title: "Lessons Learned: authoring DSS validation policies (AGID presets)"
type: lessons-learned
source: session
date: 2026-06-29
tags: [lessons-learned, dss, validation-policy, xml, xsd, agid, openapi, eidas]
lesson_count: 6
category: notes
confidence: high
summary: "Gotchas writing/validating DSS 6.4 ConstraintsParameters policy XML: timestamp-required knob, XSD validation via dss-policy-jaxb, forbidden -- in XML comments, duplicated SigningCertificate blocks, TSA EKU, AlgoExpirationDate algo names."
---

# Lessons Learned: authoring DSS validation policies (AGID presets)

> Extracted from session on 2026-06-29. 6 lessons from building the AGID / AGID_TS
> presets and reviewing an externally-supplied policy. See [[concepts/dss-policy-xml]],
> [[concepts/validation-profiles]], [[concepts/italian-digital-signature-law]].

## Lesson 1: requiring a timestamp in a DSS policy = `<TLevelTimeStamp Level="FAIL"/>`

**Category**: discovery
**Context**: Needed an AGID_TS preset that mandates a timestamp; initially unsure DSS could express "signature must be timestamped" in policy XML.
**Root cause**: The knob is `<TLevelTimeStamp Level="FAIL"/>`, a child of `UnsignedAttributes` (`UnsignedAttributesConstraints` in `policy.xsd`). It requires at least one *valid* T-level timestamp (signature-time-stamp or PAdES document-timestamp) that passes validation. `<SignatureTimeStamp>` only checks attribute *presence*, not validity, and misses document timestamps.
**Fix**: Added `<TLevelTimeStamp Level="FAIL"/>` inside `<UnsignedAttributes>` of `SignatureConstraints`.
**Rule**: To mandate a timestamp in a DSS policy use `TLevelTimeStamp` (validity) not `SignatureTimeStamp` (presence); for archival use `LTALevelTimeStamp`.

## Lesson 2: XML comments cannot contain a double hyphen `--`

**Category**: gotcha
**Context**: Generating richly-commented policy XML with `<!-- ---------- -->` divider lines.
**Symptom**: `xmllint` → `parser error : Double hyphen within comment`, repeated for every `--` pair, file fails to parse.
**Root cause**: XML spec forbids `--` inside comments.
**Fix**: Replaced dashed dividers with `=====` (and the em dash `—` is fine — it is a single char, not two hyphens).
**Rule**: Never put `--` inside XML comments; use `=` for ASCII dividers. This also breaks DSS policy loading, not just lint.

## Lesson 3: validate DSS policy XML against `policy.xsd` from `dss-policy-jaxb`

**Category**: pattern
**Context**: Reviewing an external "AgID" policy and our generated ones.
**Root cause**: Hand-written DSS policies silently malfunction (DSS ignores unknown elements or rejects with HTTP 400 "invalid validation policy") — only XSD validation catches structural errors authoritatively.
**Fix**: `unzip dss-policy-jaxb-6.4.jar 'xsd/policy.xsd'`, then `xmllint --noout --schema xsd/policy.xsd POLICY.xml`. This immediately flagged real bugs in the external policy.
**Rule**: Always XSD-validate DSS policy XML against `policy.xsd` extracted from the `dss-policy-jaxb` jar matching the DSS version, before shipping or seeding it.

## Lesson 4: the element is `<Timestamp>` / `<Revocation>`, not `<TimestampConstraints>` / `<RevocationConstraints>`

**Category**: gotcha
**Context**: External policy used `<TimestampConstraints>` and `<RevocationConstraints>` as element names.
**Symptom**: XSD error `Element 'TimestampConstraints': This element is not expected. Expected is one of ( Timestamp, Revocation, EvidenceRecord, Cryptographic, Model, eIDAS )`.
**Root cause**: `TimestampConstraints` / `RevocationConstraints` are XSD *type* names; the *element* names under `ConstraintsParameters` are `Timestamp` and `Revocation`. Also, inside `Timestamp` the `SigningCertificate` must be wrapped in `BasicSignatureConstraints`, and `<TimestampValid>` / `OCSPCertHashPresent` / `OCSPCertHashMatch` are not real elements.
**Fix**: Use the correct element names and nesting; mirror an existing valid policy (STANDARD.xml).
**Rule**: In DSS policy XML, use element names from the XSD `element` declarations (`Timestamp`, `Revocation`), not the `complexType` names.

## Lesson 5: a DSS policy has FOUR duplicated `SigningCertificate` blocks — target edits precisely

**Category**: pattern
**Context**: Hardening only the main signing certificate (nonRepudiation, QcCompliance, QcSSCD, TrustService) without touching counter-signature / timestamp / revocation certs.
**Root cause**: `SigningCertificate` (and `CACertificate`, `KeyUsage nonRepudiation`, `SignedAttributes`) appear in `SignatureConstraints`, `CounterSignatureConstraints`, `Timestamp`, and `Revocation` — identical text in several.
**Fix**: Generate from STANDARD.xml with a Python script using `re.sub(..., count=1)`; the first match is always the main `SignatureConstraints` block. Asserted afterwards (e.g. exactly one `KeyUsage Level="WARN"` left = the counter-signature).
**Rule**: When editing a DSS policy programmatically, anchor on the first occurrence (main signature) and assert occurrence counts; never blind `replace_all`.

## Lesson 6: TSA cert needs EKU timeStamping; AlgoExpirationDate wants separated algo names

**Category**: gotcha
**Context**: Reviewing the external policy's timestamp + cryptographic blocks.
**Root cause**: Two common authoring bugs. (a) The timestamp `SigningCertificate` used `<KeyUsage><Id>nonRepudiation</Id>` — wrong; a TSA cert is identified by `<ExtendedKeyUsage><Id>timeStamping</Id>` (ETSI EN 319 412-3). (b) `AlgoExpirationDate` listed JCA combined names like `SHA256withRSA`; DSS expects digest algos (`SHA256`) and encryption algos with `Size` (`<Algo Size="2048">RSA</Algo>`) separately — combined names are silently ignored, so the intended expiration never applies.
**Fix**: Keep the STANDARD/ETSI TS 119 312 crypto block and the `ExtendedKeyUsage timeStamping` TSA constraint.
**Rule**: Validate semantics too, not just XSD: TSA = ExtendedKeyUsage timeStamping; AlgoExpirationDate uses DSS algo enum names (digest + sized encryption), never JCA `<digest>with<enc>` strings.

## Related
- [[concepts/dss-policy-xml]] · [[concepts/validation-profiles]] · [[concepts/italian-digital-signature-law]]
- [[entities/dss]] · [[concepts/signature-qualification]]
