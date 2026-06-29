---
type: concept
domain: engineering
created: 2026-06-27
updated: 2026-06-27
sources:
  - sources/SRC-2026-06-27-003
  - sources/SRC-2026-06-27-001
volatility: warm
---

# DSS policy XML

The XML validation policy format consumed by [[entities/dss]], wrapped by [[concepts/validation-profiles|Validation profiles]] in [[entities/sign-verify-2]]. Defines the constraints applied during [[concepts/signature-validation]].

## The key concept: the `Level` attribute
The policy XML's `Level` attribute is the central knob — it selects **which constraint groups** are evaluated, aligned to [[concepts/baseline-profiles]] (basic / long-term / archival). This is how a profile maps to a signature level without redefining every constraint.

## Document structure
A policy contains: model declaration, a set of constraints (each with a `Level`), and basic/long-term/archival constraint blocks. Constraint shapes cover format, cryptographic strength, revocation freshness, certificate-chain, and timestamp ordering.

## In sign-verify-2
- **Presets** ship a sensible default policy; **custom profiles** persist an admin-defined policy in [[entities/verification_profile]]; **per-request overrides** layer `profileOverrides` JSON on top. The response echoes `profileUsed` + `overridesApplied`. See [[sources/SRC-2026-06-27-003]] §4.2.

## Authoring gotchas (lessons learned)

From [[../raw/notes/2026-06-29-ll-dss-policy-authoring]]:

- **Require a timestamp** with `<TLevelTimeStamp Level="FAIL"/>` (validity) inside `UnsignedAttributes`, not `<SignatureTimeStamp>` (presence only); `LTALevelTimeStamp` for archival.
- **Element names** under `ConstraintsParameters` are `Timestamp` / `Revocation`, **not** the XSD type names `TimestampConstraints` / `RevocationConstraints`. Inside `Timestamp`, `SigningCertificate` must be wrapped in `BasicSignatureConstraints`.
- **No `--` in XML comments** (use `=` dividers) — breaks both `xmllint` and DSS policy loading.
- **Four duplicated `SigningCertificate` blocks** (SignatureConstraints / CounterSignature / Timestamp / Revocation): edit the first occurrence to target the main signature; assert occurrence counts, never blind replace-all.
- **TSA cert** = `ExtendedKeyUsage timeStamping` (ETSI EN 319 412-3), not `KeyUsage nonRepudiation`.
- **`AlgoExpirationDate`** uses DSS algo-enum names — digest (`SHA256`) and sized encryption (`<Algo Size="2048">RSA</Algo>`) **separately**; JCA combined names (`SHA256withRSA`) are silently ignored.
- **Always XSD-validate**: `xmllint --schema policy.xsd` with `policy.xsd` extracted from the `dss-policy-jaxb` jar of the matching DSS version.

## Related
- [[concepts/validation-profiles]] · [[concepts/baseline-profiles]]
- [[entities/dss]] · [[entities/verification_profile]] · [[concepts/signature-validation]]
