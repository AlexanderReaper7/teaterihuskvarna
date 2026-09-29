-- Members reach Brevo as contacts on one list, kept up to date on every
-- change, instead of a new list per mailing. The user decided this on
-- 2026-09-28: docs/decisions/0026-outbox-and-brevo-contacts.md.
--
-- A mailing now goes to that list or to a segment the association made in
-- Brevo, so the application no longer knows how many it reaches before it is
-- sent, and has no list of its own to remember. Brevo's report gives the
-- number sent.
ALTER TABLE mailing DROP COLUMN recipients;
ALTER TABLE mailing DROP COLUMN brevo_list_id;

-- A contact's LAST_SHIFT is the latest shift the member was booked on that
-- has started, so it changes when a shift starts, with no change in the
-- database. A job reports each shift once when it has started, and this
-- marks the ones it has. Shifts that started before this migration count as
-- reported, since every contact is synced below.
ALTER TABLE volunteer_shift ADD COLUMN start_reported BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE volunteer_shift SET start_reported = TRUE WHERE starts_at <= now();

-- Every member with an account, once, so the list starts complete.
INSERT INTO outbox (kind, payload)
SELECT 'brevo-contact', member_id::text FROM account ORDER BY member_id;
