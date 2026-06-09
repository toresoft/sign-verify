-- Some EU Trusted List certificates carry data that overflows the original
-- VARCHAR(500) columns, causing the TSL mirror sync to fail partway through:
--   * certificate_der_b64 -- base64 of the full DER (~1.5-3 KB); already TEXT in
--     the current V1 baseline, widened here defensively to repair databases that
--     were created from an older schema where this column was VARCHAR(500).
--   * subject_dn / issuer_dn -- RFC 2253 distinguished names of some QTSP/QC
--     certificates exceed 500 characters.
--   * tsl_url -- pivot / TL URLs can be long.
-- `SET DATA TYPE` is the SQL-standard form accepted by both PostgreSQL and H2.
-- Widening VARCHAR -> TEXT is a no-op on databases where the column is already
-- TEXT, so this migration is safe to apply everywhere.
ALTER TABLE trusted_certificate ALTER COLUMN certificate_der_b64 SET DATA TYPE TEXT;
ALTER TABLE trusted_certificate ALTER COLUMN subject_dn SET DATA TYPE TEXT;
ALTER TABLE trusted_certificate ALTER COLUMN issuer_dn SET DATA TYPE TEXT;
ALTER TABLE trusted_certificate ALTER COLUMN tsl_url SET DATA TYPE TEXT;
