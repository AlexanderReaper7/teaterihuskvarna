-- Work that has to reach another system after a commit: a mail, or a change to
-- a Brevo contact. docs/decisions/0026-outbox-and-brevo-contacts.md.
--
-- The row is written in the transaction that causes the work, so the work
-- exists exactly when the change does. Outbox tries it at once after the
-- commit, and a scheduled job retries what failed, with a growing wait, until
-- it succeeds. A row is deleted once its work is done, so the table holds only
-- what is still pending.
--
-- A mail's payload holds the whole mail, so a login link sits here in plain
-- text until it is sent, usually a second, at most as long as the link lives.

CREATE TABLE outbox (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind             VARCHAR(40)   NOT NULL,
    payload          TEXT          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    attempts         INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- The exception's class from the latest failure. Not its message, which
    -- may hold an address.
    last_error       VARCHAR(300)
);

CREATE INDEX outbox_due ON outbox (next_attempt_at);
