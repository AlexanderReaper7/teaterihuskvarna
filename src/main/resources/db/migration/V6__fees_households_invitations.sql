-- Fees (R013, R019) and invitations to a household member without an account
-- (R019). Households themselves have existed since V1.
--
-- A fee row is one payment for one year. member_id goes NULL when the member is
-- deleted, and the year, kind and amount stay, so the income per year can still
-- be counted after a member leaves (the user's decision of 2026-09-28).
-- marked_by goes NULL the same way, although administrators are only marked
-- removed today, never deleted.
--
-- Whether a member has paid a year is not stored. It is computed: a fee row of
-- their own for the year, or a HOUSEHOLD row for the year from anyone in their
-- household now. Moving a member changes which household fee covers them, as
-- the user decided on 2026-09-28.
--
-- The unique constraint allows any number of rows with a NULL member_id, since
-- PostgreSQL treats NULLs as distinct, so anonymised rows from several deleted
-- members can share a year.

CREATE TABLE fee (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id   BIGINT       REFERENCES member (id) ON DELETE SET NULL,
    year        INTEGER      NOT NULL,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('INDIVIDUAL', 'HOUSEHOLD')),
    amount_ore  INTEGER      NOT NULL CHECK (amount_ore >= 0),
    paid_at     TIMESTAMPTZ  NOT NULL,
    marked_by   BIGINT       REFERENCES administrator (id) ON DELETE SET NULL,
    UNIQUE (member_id, year)
);

CREATE INDEX fee_year ON fee (year);

-- An invitation for a member without an account to create one with this
-- address. One open invitation per member: sending again replaces the row, and
-- with it the token, so the older link stops working. Only the SHA-256 of the
-- token is stored, as for login links and membership applications.
CREATE TABLE invitation (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id   BIGINT       NOT NULL UNIQUE REFERENCES member (id) ON DELETE CASCADE,
    email       VARCHAR(254) NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL
);
