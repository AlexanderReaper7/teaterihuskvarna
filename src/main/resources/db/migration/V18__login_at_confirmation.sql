-- Confirming a membership application in the browser that sent the form logs
-- the new member in from the welcome page, without a mail (#47):
-- docs/decisions/0015, "Logging in from the welcome page".

ALTER TABLE membership_application
    -- The SHA-256 of the login cookie the form set, as in one_time_token.
    -- NULL for an application sent before this column existed, which confirms
    -- without a login.
    ADD COLUMN browser_hash VARCHAR(64);

-- The login link made at confirmation is shown on the welcome page and never
-- mailed, so it has no code to type in another browser.
ALTER TABLE one_time_token ALTER COLUMN code_hash DROP NOT NULL;
