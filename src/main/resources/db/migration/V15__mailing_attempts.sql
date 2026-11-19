-- A mailing is saved before Brevo is asked for its draft, in status
-- creating and without a campaign, so an attempt whose answer is lost is still
-- known. Its number is in the campaign's name, and the application finds the
-- campaign by that name: docs/decisions/0026-outbox-and-brevo-contacts.md.
ALTER TABLE mailing ALTER COLUMN brevo_campaign_id DROP NOT NULL;

-- Only an attempt waits for its campaign.
ALTER TABLE mailing ADD CONSTRAINT mailing_campaign_unless_creating
    CHECK (status = 'creating' OR brevo_campaign_id IS NOT NULL);

-- A campaign is one mailing.
ALTER TABLE mailing ADD CONSTRAINT mailing_campaign_once UNIQUE (brevo_campaign_id);

-- One attempt at a time. A second one, from a double click or a retry while
-- Brevo may still make the first, fails to insert.
CREATE UNIQUE INDEX mailing_one_creating ON mailing (status) WHERE status = 'creating';
