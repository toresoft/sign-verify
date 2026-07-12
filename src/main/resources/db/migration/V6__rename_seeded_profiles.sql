-- Rename the pre-seeded AgID profiles from their historical lowercase names to the
-- uppercase preset names, so a profile can be selected by a name that matches its
-- preset. Uniqueness on verification_profile.name is already enforced (V1). On a
-- fresh database these rows do not exist yet and the UPDATEs are a no-op; the seeder
-- then creates the uppercase names directly.
UPDATE verification_profile SET name = 'AGID' WHERE name = 'agid';
UPDATE verification_profile SET name = 'AGID_TS' WHERE name = 'agid-ts';
