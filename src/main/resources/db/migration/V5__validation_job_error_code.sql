-- Stable machine-readable error code (see Errors catalog) for failed jobs: clients get a
-- catalog code instead of raw exception text, which must never leave the service.
ALTER TABLE validation_job ADD COLUMN error_code VARCHAR(64);

-- Backfill existing failed rows so every FAILED job carries a catalog code.
UPDATE validation_job SET error_code = 'job.expired' WHERE error_message = 'job_expired';
UPDATE validation_job
   SET error_code = 'internal-error'
 WHERE status = 'FAILED'
   AND error_code IS NULL;
