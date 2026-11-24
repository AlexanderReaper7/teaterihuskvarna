-- The database queues a member's Brevo contact itself, on every write to a
-- table the contact is read from: docs/decisions/0026-outbox-and-brevo-contacts.md.
--
-- Until this migration the services queued it: one
-- publishEvent(new MemberChanged(id)) beside each change, 18 of them in 8
-- classes. A change without one left the contact as it was, and nothing
-- failed. A trigger is not something the code that writes can leave out. It
-- fires for a service, for a bulk query, for the rows a foreign key deletes
-- and for a statement typed into psql.
--
-- The rule goes by table and not by column: any insert, update or delete of a
-- row queues every member the row can concern. So a contact is sometimes sent
-- again with nothing new in it, as when a booking is marked as reminded.
-- BrevoContacts reads the member as they are when the row is done, so that
-- costs a call to Brevo and changes nothing there.
--
-- A new table that the contact is read from needs a trigger here.
-- BrevoContactQueueIT asks PostgreSQL which tables BrevoContacts reads, and
-- fails when one of them has no trigger for insert, update and delete.

-- Queues one member, once per transaction.
CREATE FUNCTION brevo_contact_queue(changed_member BIGINT) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    IF changed_member IS NULL THEN
        RETURN;
    END IF;
    -- xmin is the transaction that wrote a row, so NOT EXISTS reads "this
    -- transaction has not queued the member yet". A row from an earlier
    -- transaction does not count: its attempt may already have read the
    -- member as they were before this change.
    INSERT INTO outbox (kind, payload)
    SELECT 'brevo-contact', changed_member::text
    WHERE NOT EXISTS (
        SELECT 1 FROM outbox
        WHERE kind = 'brevo-contact'
          AND payload = changed_member::text
          AND xmin = pg_current_xact_id()::xid);
    -- Outbox tries a row it adds itself as soon as its transaction commits.
    -- It did not add this one, so PostgreSQL tells it: a notification is
    -- delivered at the commit, once per transaction, and never after a
    -- rollback. OutboxNotifications listens.
    PERFORM pg_notify('outbox', 'brevo-contact');
END;
$$;

-- member: the row is the member.
CREATE FUNCTION brevo_contact_of_member() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM brevo_contact_queue(OLD.id);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM brevo_contact_queue(NEW.id);
    END IF;
    RETURN NULL;
END;
$$;

-- account, offer_registration, volunteer_booking: the row names its member.
CREATE FUNCTION brevo_contact_of_member_id() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM brevo_contact_queue(OLD.member_id);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM brevo_contact_queue(NEW.member_id);
    END IF;
    RETURN NULL;
END;
$$;

-- fee: the payer, and for a fee that names a household everyone in that
-- household now, since it covers them all (docs/decisions/0025).
CREATE FUNCTION brevo_contact_of_fee() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM brevo_contact_queue(OLD.member_id);
        PERFORM brevo_contact_queue(id) FROM member WHERE household_id = OLD.household_id;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM brevo_contact_queue(NEW.member_id);
        PERFORM brevo_contact_queue(id) FROM member WHERE household_id = NEW.household_id;
    END IF;
    RETURN NULL;
END;
$$;

-- volunteer_shift: everyone booked on it. A new shift has no bookings, and a
-- deleted one has lost them to its foreign key, which queued each member from
-- volunteer_booking, so in practice this is for an update: a changed start,
-- and start_reported, which ShiftService sets once the shift has started.
CREATE FUNCTION brevo_contact_of_shift() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM brevo_contact_queue(member_id) FROM volunteer_booking WHERE shift_id = OLD.id;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        PERFORM brevo_contact_queue(member_id) FROM volunteer_booking WHERE shift_id = NEW.id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER brevo_contact AFTER INSERT OR UPDATE OR DELETE ON member
    FOR EACH ROW EXECUTE FUNCTION brevo_contact_of_member();

CREATE TRIGGER brevo_contact AFTER INSERT OR UPDATE OR DELETE ON account
    FOR EACH ROW EXECUTE FUNCTION brevo_contact_of_member_id();

CREATE TRIGGER brevo_contact AFTER INSERT OR UPDATE OR DELETE ON offer_registration
    FOR EACH ROW EXECUTE FUNCTION brevo_contact_of_member_id();

CREATE TRIGGER brevo_contact AFTER INSERT OR UPDATE OR DELETE ON volunteer_booking
    FOR EACH ROW EXECUTE FUNCTION brevo_contact_of_member_id();

CREATE TRIGGER brevo_contact AFTER INSERT OR UPDATE OR DELETE ON fee
    FOR EACH ROW EXECUTE FUNCTION brevo_contact_of_fee();

CREATE TRIGGER brevo_contact AFTER INSERT OR UPDATE OR DELETE ON volunteer_shift
    FOR EACH ROW EXECUTE FUNCTION brevo_contact_of_shift();
