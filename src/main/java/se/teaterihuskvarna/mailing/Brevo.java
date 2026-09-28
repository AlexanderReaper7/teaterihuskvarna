package se.teaterihuskvarna.mailing;

/// The calls to Brevo's API that a mailing needs: `docs/decisions/0023-brevo-list-per-mailing.md`.
///
/// [HttpBrevo] makes them; [FakeBrevo] keeps them in memory for development and
/// the tests, which must never reach the real account.
interface Brevo {

    /// @param name the list's name as Brevo shows it
    /// @return the new list's id
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    long createList(String name);

    /// Creates the contact, or adds an existing one to the list. Never sets
    /// the blacklist fields, so a member who unsubscribed stays unsubscribed.
    ///
    /// @param email  the address
    /// @param listId the list to add it to
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    void addContact(String email, long listId);

    /// @param campaign the subject, the HTML and the list
    /// @return the draft campaign's id
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    long createDraft(Campaign campaign);

    /// Sends the campaign to one address only. Brevo sends a test only to an
    /// existing contact on some list, so the caller adds the address first.
    ///
    /// @param campaignId the campaign
    /// @param email      where the test goes
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    void sendTest(long campaignId, String email);

    /// @param campaignId the campaign
    /// @return its status and numbers
    /// @throws BrevoUnavailable if Brevo refuses or cannot be reached
    CampaignReport report(long campaignId);

    /// A campaign as the application hands it over.
    ///
    /// @param name    the name in Brevo's campaign list
    /// @param subject the subject line
    /// @param html    the whole mail, with Brevo's `{{ unsubscribe }}` in it
    /// @param listId  the list it goes to
    record Campaign(String name, String subject, String html, long listId) {
    }
}
