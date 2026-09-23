-- The member register as projektplan.md commits to it: name, phone, address,
-- household. The email address belongs to an account, not to a member (V2),
-- because a member added to a household may have none. No personal identity
-- number, by decision.
--
-- This file was edited on 2026-09-23 to move email out of member, which is only
-- allowed because no test or production database had run it yet. From the first
-- deploy on, a change is a new migration: docs/decisions/0010.
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
    phone         VARCHAR(32),
    address       VARCHAR(200),
    postal_code   VARCHAR(10),
    city          VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL
);
