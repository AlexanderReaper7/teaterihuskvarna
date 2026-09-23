-- The member register as projektplan.md commits to it: name, email, phone,
-- address, household. No personal identity number, by decision.
--
-- VARCHAR(n), never CHAR(n) and never TEXT. CHAR comes back through JDBC as
-- bpchar and fails Hibernate's validator; TEXT is untested under validate. This
-- rule is written here as well as in docs/decisions/0012 because here is where
-- it gets broken.
--
-- There is deliberately no fee table yet. The fee rule in projektplan.md is
-- provisional until it has been checked against real anonymised bankgiro
-- examples, and a table written before that check would have to be migrated
-- afterwards anyway. See docs/decisions/0010-flyway-for-migrations.md.

CREATE TABLE household (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE member (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    household_id  BIGINT       REFERENCES household (id),
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone         VARCHAR(32),
    address       VARCHAR(200),
    postal_code   VARCHAR(10),
    city          VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- Login is by email address, so two members cannot share one. Matched case
-- insensitively, or anna@ and Anna@ are two members nobody can tell apart.
CREATE UNIQUE INDEX member_email_key ON member (LOWER(email));
