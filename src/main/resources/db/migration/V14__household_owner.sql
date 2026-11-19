-- Member creation records its owner; NULL means administrators manage it.
-- No historical creator is inferred. Existing rows and fee coverage stay intact.
-- Deleting the member owner returns management to administrators.
ALTER TABLE household ADD COLUMN owner_member_id BIGINT REFERENCES member (id) ON DELETE SET NULL;
CREATE INDEX household_owner ON household (owner_member_id) WHERE owner_member_id IS NOT NULL;
