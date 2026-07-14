-- Current validation phase of a RUNNING job, so a stuck in-flight job is diagnosable via
-- GET /jobs/{id} and directly in the DB without grepping logs. Both columns are nullable and
-- populated only while the job is RUNNING.
ALTER TABLE validation_job ADD COLUMN phase VARCHAR(20);
ALTER TABLE validation_job ADD COLUMN phase_updated_at TIMESTAMP NULL;
