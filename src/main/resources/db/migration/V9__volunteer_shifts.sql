-- Volunteer shifts at performances and the members who booked them: R016,
-- R017 and R020. docs/decisions/0024-volunteer-shifts.md.

-- A shift belongs to one Sanity event, by the published document's id. The
-- event's title and slug are copied when the shift is made, so the booking
-- page, the export and the reminder still read if an editor unpublishes it.
CREATE TABLE volunteer_shift (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     VARCHAR(100) NOT NULL,
    event_title  VARCHAR(200) NOT NULL,
    event_slug   VARCHAR(200) NOT NULL,
    -- The two tasks R016 names, cloakroom and serving.
    task         VARCHAR(20)  NOT NULL CHECK (task IN ('GARDEROB', 'SERVERING')),
    starts_at    TIMESTAMPTZ  NOT NULL,
    ends_at      TIMESTAMPTZ  NOT NULL,
    places       INTEGER      NOT NULL CHECK (places > 0),
    created_at   TIMESTAMPTZ  NOT NULL,
    CHECK (ends_at > starts_at)
);

CREATE INDEX volunteer_shift_starts_at ON volunteer_shift (starts_at);

-- One booking per member per shift. Deleting the shift or the member deletes
-- the booking with it. reminded_at is set when R017's reminder went out, so
-- the hourly job sends it once.
CREATE TABLE volunteer_booking (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shift_id     BIGINT       NOT NULL REFERENCES volunteer_shift (id) ON DELETE CASCADE,
    member_id    BIGINT       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL,
    reminded_at  TIMESTAMPTZ,
    UNIQUE (shift_id, member_id)
);

CREATE INDEX volunteer_booking_member_id ON volunteer_booking (member_id);
