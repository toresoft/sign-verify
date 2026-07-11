# TODOS

## Trusted-path validation fixture (TOTAL_PASSED) in `mvn verify`

- **What:** Add a deterministic test fixture — a validly signed document with a frozen
  test keystore/TSL — asserting `TOTAL_PASSED` through the full validation pipeline.
- **Why:** `SivaCorpusValidationTest` (181 fixtures) covers only trust-independent
  invariants (INDETERMINATE-family indications without TSL, format recognition). The
  positive verdict path — the reason the service exists — is exercised only by manual
  smoke tests and staging soaks. A regression on `TOTAL_PASSED` would pass every
  automated gate today.
- **Pros:** Closes the coverage gap on the positive verdict permanently; deterministic
  (frozen trust material), runs in `mvn verify` on every PR.
- **Cons:** Frozen fixture needs renewal when test certificates expire; ~half a day of
  human effort (CC: ~30-45 min) to wire the frozen TSL/keystore.
- **Context:** Surfaced during the Spring Boot 4.1 migration plan review (2026-07-11,
  outside-voice finding OV-4; user accepted the residual risk for the migration scope —
  see design doc `~/.gstack/projects/toresoft-sign-verify/salvatore-develop-design-20260710-231603.md`,
  Success Criteria "Perimetro onesto"). The repo already has test trust infrastructure
  (calibrated no-TSL DSS behaviour in the corpus test) — start from
  `src/test/java/org/toresoft/signverify/adapter/dss/SivaCorpusValidationTest.java` and
  the existing test keystore setup.
- **Depends on / blocked by:** Nothing — feasible on 3.5 today or after the Boot 4.1
  migration. If done before T2, it strengthens the migration gates for free.
