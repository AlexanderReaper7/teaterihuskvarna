-- Accounts, administrators, membership applications, and the two tables login
-- links need: the hashed tokens and the request counts behind the rate limit.
-- The rules are in docs/projektplan.md, "Member login must not reveal
-- membership"; the mechanism in docs/decisions/0015.

-- A member's login. One per member at most, and an address belongs to one
-- account at most, matched case insensitively, or anna@ and Anna@ would be two
-- accounts nobody can tell apart.
CREATE TABLE account (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id   BIGINT       NOT NULL UNIQUE REFERENCES member (id) ON DELETE CASCADE,
    email       VARCHAR(254) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX account_email_key ON account (LOWER(email));

-- Separate from account: an administrator need not be a member. A removed
-- administrator keeps the row, because fee records will name who marked them
-- paid. Re-adding the same address reactivates the row, which is why the
-- address is unique across removed rows too.
CREATE TABLE administrator (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  BIGINT       REFERENCES administrator (id),
    removed_at  TIMESTAMPTZ,
    removed_by  BIGINT       REFERENCES administrator (id)
);

CREATE UNIQUE INDEX administrator_email_key ON administrator (LOWER(email));

-- A Bli medlem submission nobody has confirmed yet. Kept out of member so that
-- a bot filling the form never reaches the register or a mailing. One pending
-- application per address: applying again replaces the token.
CREATE TABLE membership_application (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(254) NOT NULL,
    phone        VARCHAR(32),
    address      VARCHAR(200),
    postal_code  VARCHAR(10),
    city         VARCHAR(100),
    token_hash   VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL
);

CREATE UNIQUE INDEX membership_application_email_key ON membership_application (LOWER(email));
CREATE UNIQUE INDEX membership_application_token_key ON membership_application (token_hash);

-- Login links. The SHA-256 of the token, never the token: a reader of this
-- table or of a backup cannot log in as anyone. The address is stored in lower
-- case.
CREATE TABLE one_time_token (
    token_hash  VARCHAR(64)  PRIMARY KEY,
    kind        VARCHAR(16)  NOT NULL CHECK (kind IN ('member', 'administrator')),
    email       VARCHAR(254) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX one_time_token_expires_at ON one_time_token (expires_at);

-- One row per request for a login link or an application mail, counted per
-- address and per client address. Rows older than the window are deleted, and
-- the client addresses with them.
CREATE TABLE link_request (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    client_address  VARCHAR(45)  NOT NULL,
    requested_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX link_request_email ON link_request (email, requested_at);
CREATE INDEX link_request_client_address ON link_request (client_address, requested_at);
