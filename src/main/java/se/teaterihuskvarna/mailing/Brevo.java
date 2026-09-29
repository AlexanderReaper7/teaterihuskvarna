package se.teaterihuskvarna.mailing;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// The calls to Brevo's API the application makes:
/// `docs/decisions/0026-outbox-and-brevo-contacts.md`.
///
/// [HttpBrevo] makes them; [FakeBrevo] keeps them in memory for development and
/// the tests, which must never reach the real account.
interface Brevo {

    /// Creates or updates a member's contact and puts it on the members' list.
    /// Never unblocks a contact, so a member who unsubscribed stays
    /// unsubscribed, a new address included.
    ///
    /// @param contact what the contact shows
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    void saveContact(Contact contact);

    /// Deletes a member's contact, if there is one.
    ///
    /// @param memberId the member, who is the contact's `ext_id`
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    void deleteContact(long memberId);

    /// @return the segments the association made in Brevo, by name
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    List<Segment> segments();

    /// Counts who a campaign to the list or to a segment would reach.
    ///
    /// @param segmentId the segment, or null for the whole members' list
    /// @return the members it reaches and the contacts in it that are not members
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    MailingReach reach(@Nullable Long segmentId);

    /// @param campaign the subject, the HTML and who it goes to
    /// @return the draft campaign's id
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    long createDraft(Campaign campaign);

    /// Sends the campaign to one address only. Brevo sends a test only to an
    /// existing contact on some list, so the address joins the test list first.
    ///
    /// @param campaignId the campaign
    /// @param email      where the test goes
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    void sendTest(long campaignId, String email);

    /// @param campaignId the campaign
    /// @return its status and numbers
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    CampaignReport report(long campaignId);

    /// A member as their Brevo contact shows them. The association builds
    /// its audiences in Brevo as segments on these attributes.
    ///
    /// @param memberId  the member, stored as the contact's `ext_id`
    /// @param email     the account's address
    /// @param name      the member's full name, attribute NAMN
    /// @param paidYear  the latest year the fee is paid, attribute PAID_YEAR, or null
    /// @param lastShift the day the latest booked shift started, attribute LAST_SHIFT, or null
    /// @param offerIds  the offers the member is registered for, attribute OFFERS as `;12;15;`
    record Contact(long memberId, String email, String name, @Nullable Integer paidYear,
            @Nullable LocalDate lastShift, List<Long> offerIds) {

        public Contact {
            offerIds = List.copyOf(offerIds);
        }

        /// @return OFFERS as Brevo stores it: each id between semicolons, so
        ///         a segment can ask whether it contains `;12;`, or empty
        public String offers() {
            if (offerIds.isEmpty()) {
                return "";
            }
            StringBuilder text = new StringBuilder(";");
            for (long id : offerIds) {
                text.append(id).append(';');
            }
            return text.toString();
        }
    }

    /// @param id   Brevo's id for it
    /// @param name its name in Brevo
    record Segment(long id, String name) {
    }

    /// A campaign as the application hands it over.
    ///
    /// @param name      the name in Brevo's campaign list
    /// @param subject   the subject line
    /// @param html      the whole mail, with Brevo's `{{ unsubscribe }}` in it
    /// @param segmentId the segment it goes to, or null for the whole members' list
    record Campaign(String name, String subject, String html, @Nullable Long segmentId) {
    }
}
