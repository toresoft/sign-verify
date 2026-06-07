-- key_prefix is looked up via findByKeyPrefix (returns Optional): a prefix collision would otherwise
-- raise NonUniqueResultException (HTTP 500) during authentication. Enforce uniqueness at the DB
-- level; the plain index becomes redundant once the unique constraint provides its own index.
DROP INDEX idx_api_key_prefix;
ALTER TABLE api_key ADD CONSTRAINT uq_api_key_prefix UNIQUE (key_prefix);
