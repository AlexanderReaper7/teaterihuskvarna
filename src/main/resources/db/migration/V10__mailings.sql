-- One row per mailing an administrator prepared: R022 to R025, and the log
-- R025 asks for. The mail itself, and who unsubscribed, live in Brevo. The
-- application keeps what it chose and what Brevo last reported, so the log
-- still reads after Brevo has deleted an old campaign.
-- docs/decisions/0023-brevo-list-per-mailing.md.
CREATE TABLE mailing (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject            VARCHAR(150) NOT NULL,
    -- The audience rule as the form sent it, such as PAID or OFFER:12, and its
    -- Swedish name when the mailing was made, since an offer may be deleted.
    audience           VARCHAR(40)  NOT NULL,
    audience_name      VARCHAR(300) NOT NULL,
    recipients         INTEGER      NOT NULL CHECK (recipients >= 0),
    brevo_list_id      BIGINT       NOT NULL,
    brevo_campaign_id  BIGINT       NOT NULL,
    -- Null once the administrator's row is gone. Removed administrators keep
    -- their row (V2), so in practice this stays set.
    created_by         BIGINT       REFERENCES administrator (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    -- Brevo's status and numbers when the log last asked.
    status             VARCHAR(20)  NOT NULL,
    sent_at            TIMESTAMPTZ,
    sent               INTEGER      NOT NULL DEFAULT 0,
    delivered          INTEGER      NOT NULL DEFAULT 0,
    unique_views       INTEGER      NOT NULL DEFAULT 0,
    unsubscriptions    INTEGER      NOT NULL DEFAULT 0,
    hard_bounces       INTEGER      NOT NULL DEFAULT 0,
    checked_at         TIMESTAMPTZ  NOT NULL
);
