-- Offers and the registrations against them, R014 and R020. Both live here
-- rather than in Sanity, by the user's decision of 2026-09-28, so that taking
-- a place and checking the capacity happen in one transaction.
CREATE TABLE offer (
    id                      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title                   VARCHAR(200) NOT NULL,
    -- Plain text. The pages show its line breaks and nothing else.
    description             VARCHAR(10000) NOT NULL,
    -- When the offer happens, if it happens at a time at all.
    starts_at               TIMESTAMPTZ,
    -- After this, members can neither register nor cancel. Null falls back
    -- to starts_at, and with both null registration never closes.
    registration_closes_at  TIMESTAMPTZ,
    -- Null is unlimited. An administrator may lower it below the number
    -- already registered; places left then shows 0, never less.
    capacity                INTEGER      CHECK (capacity >= 0),
    -- Members see only published offers.
    published               BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL
);

-- One place per member per offer. Deleting the offer or the member deletes the
-- registration with it.
CREATE TABLE offer_registration (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_id    BIGINT       NOT NULL REFERENCES offer (id) ON DELETE CASCADE,
    member_id   BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL,
    UNIQUE (offer_id, member_id)
);

-- The unique constraint serves lookups by offer. This one serves the cascade
-- when a member is deleted, and a member's own list.
CREATE INDEX offer_registration_member_id ON offer_registration (member_id);
