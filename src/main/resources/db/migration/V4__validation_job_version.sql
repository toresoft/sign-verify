-- Optimistic-lock column for validation_job: workers and the cleanup scheduler mutate the same
-- rows concurrently; @Version turns silent last-writer-wins overwrites into detectable conflicts.
ALTER TABLE validation_job ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
