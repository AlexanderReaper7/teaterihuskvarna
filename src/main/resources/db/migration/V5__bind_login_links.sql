-- A login link works only in the browser that asked for it, and the mail
-- carries a code for reading it on another device: docs/decisions/0015,
-- "A link works only in the browser that asked for it".
--
-- Links issued before this migration have no browser and could never be
-- redeemed, so they are deleted rather than given a placeholder. They last an
-- hour at most, and the person asks again.
DELETE FROM one_time_token;

ALTER TABLE one_time_token
    -- The SHA-256 of the browser's login cookie, 256 random bits like a token.
    ADD COLUMN browser_hash VARCHAR(64) NOT NULL,
    -- The SHA-256 of the six-digit code. Anyone can reverse it by trying all
    -- million codes, which gains nothing without the browser's cookie.
    ADD COLUMN code_hash    VARCHAR(64) NOT NULL,
    -- Codes typed against this link. The code stops working at five; the
    -- link does not.
    ADD COLUMN code_tries   SMALLINT    NOT NULL DEFAULT 0;

CREATE INDEX one_time_token_browser_hash ON one_time_token (browser_hash);
