-- A household fee keeps the household it was paid for, so it still covers the
-- household after the member who paid is deleted and member_id goes NULL. The
-- user decided this on 2026-09-28. Before, the fee found its household through
-- the payer, and deleting the payer left the rest of the household unpaid.
--
-- household_id is set for HOUSEHOLD rows whose payer was in a household, and
-- NULL otherwise. A household fee from a payer in no household covers only the
-- payer, through member_id, as before.

ALTER TABLE fee ADD COLUMN household_id BIGINT REFERENCES household (id) ON DELETE SET NULL;

UPDATE fee SET household_id = member.household_id
FROM member
WHERE fee.member_id = member.id AND fee.kind = 'HOUSEHOLD';

CREATE INDEX fee_household ON fee (household_id) WHERE household_id IS NOT NULL;
