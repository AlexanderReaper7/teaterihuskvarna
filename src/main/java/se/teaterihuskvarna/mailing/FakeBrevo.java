package se.teaterihuskvarna.mailing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/// Keeps every call in memory and sends nothing, for development and the
/// tests. A campaign reports `draft` until [#markSent] is called, the way a
/// real one does until an administrator sends it in Brevo. It has no
/// segments until a test gives it some with [#segments(List)]. A test can
/// make it refuse every campaign call with [#refuseCampaigns(boolean)].
final class FakeBrevo implements Brevo {

    private final JdbcClient jdbc;
    private final AtomicLong ids = new AtomicLong(100);
    private final Map<Long, Contact> contacts = new LinkedHashMap<>();
    private final Map<Long, Campaign> campaigns = new LinkedHashMap<>();
    private final Map<Long, CampaignReport> reports = new LinkedHashMap<>();
    private final List<String> tests = new ArrayList<>();
    private List<Segment> segments = List.of();
    private final Map<Long, MailingReach> reaches = new LinkedHashMap<>();
    private boolean refusing;
    private DraftAnswer draftAnswer = DraftAnswer.ANSWER;
    private boolean listingLate;
    private boolean refusingLists;
    private boolean refusingCounts;

    /// @param jdbc the database, whose accounts are the members' list
    FakeBrevo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public synchronized void saveContact(Contact contact) {
        contacts.put(contact.memberId(), contact);
    }

    @Override
    public synchronized void deleteContact(long memberId) {
        contacts.remove(memberId);
    }

    @Override
    public synchronized List<Segment> segments() {
        return segments;
    }

    /// The list reaches every member with an account, as the real list does
    /// once [BrevoContacts] has caught up. The contacts the fake was handed
    /// would undercount it: the fake forgets its contacts on a restart while
    /// the database keeps its accounts. A segment reaches what a test set
    /// with [#reach(long, MailingReach)], or nobody.
    @Override
    public MailingReach reach(@Nullable Long segmentId) {
        synchronized (this) {
            if (refusingCounts) {
                throw new BrevoRefused("refusing to count");
            }
        }
        if (segmentId == null) {
            return new MailingReach(jdbc.sql("SELECT count(*) FROM account").query(Long.class).single(), 0);
        }
        synchronized (this) {
            return reaches.getOrDefault(segmentId, new MailingReach(0, 0));
        }
    }

    @Override
    public synchronized long createDraft(Campaign campaign) {
        refuseIfAsked();
        Long segment = campaign.segmentId();
        if (segment != null && segments.stream().noneMatch(s -> s.id() == segment)) {
            throw new BrevoRefused("no segment " + segment);
        }
        if (draftAnswer == DraftAnswer.REFUSE) {
            throw new BrevoRefused("refusing the draft");
        }
        if (draftAnswer == DraftAnswer.TIME_OUT) {
            throw new BrevoUnavailable("timed out before making the draft");
        }
        long id = ids.incrementAndGet();
        campaigns.put(id, campaign);
        reports.put(id, new CampaignReport("draft", null, 0, 0, 0, 0, 0));
        if (draftAnswer == DraftAnswer.LOSE) {
            throw new BrevoUnavailable("made campaign " + id + " but lost the answer");
        }
        return id;
    }

    @Override
    public synchronized List<Long> campaignsNamed(String name) {
        refuseIfAsked();
        if (refusingLists) {
            throw new BrevoRefused("refusing to list campaigns");
        }
        if (listingLate) {
            return List.of();
        }
        List<Long> named = new ArrayList<>();
        for (Map.Entry<Long, Campaign> made : campaigns.entrySet()) {
            if (made.getValue().name().equals(name)) {
                named.addFirst(made.getKey());
            }
        }
        return named;
    }

    @Override
    public synchronized void sendTest(long campaignId, String email) {
        refuseIfAsked();
        if (!campaigns.containsKey(campaignId)) {
            throw new BrevoUnavailable("no campaign " + campaignId);
        }
        tests.add(campaignId + " " + email);
    }

    @Override
    public synchronized CampaignReport report(long campaignId) {
        refuseIfAsked();
        CampaignReport report = reports.get(campaignId);
        if (report == null) {
            throw new BrevoUnavailable("no campaign " + campaignId);
        }
        return report;
    }

    /// @return every contact, by member id
    synchronized Map<Long, Contact> contacts() {
        return Map.copyOf(contacts);
    }

    /// Forgets every contact, as a test that starts from an empty register needs.
    synchronized void forgetContacts() {
        contacts.clear();
    }

    /// @param segmentId a segment
    /// @param reach     who it reaches from now on
    synchronized void reach(long segmentId, MailingReach reach) {
        reaches.put(segmentId, reach);
    }

    /// @param given the segments Brevo has from now on
    synchronized void segments(List<Segment> given) {
        segments = List.copyOf(given);
        reaches.clear();
    }

    /// How [#createDraft] answers.
    enum DraftAnswer {
        /// Makes the campaign and answers with its id.
        ANSWER,
        /// Makes the campaign and fails, as when Brevo's answer outlasts the timeout.
        LOSE,
        /// Makes nothing and fails, as when the request times out before Brevo acts.
        TIME_OUT,
        /// Makes nothing and answers with an error.
        REFUSE
    }

    /// @param answer how [#createDraft] answers from now on
    synchronized void answerDrafts(DraftAnswer answer) {
        draftAnswer = answer;
    }

    /// @param late whether [#campaignsNamed] finds nothing from now on, as
    ///             when Brevo has not finished making a campaign it was asked for
    synchronized void listCampaignsLate(boolean late) {
        listingLate = late;
    }

    /// @param refuse whether [#campaignsNamed] fails from now on
    synchronized void refuseCampaignLists(boolean refuse) {
        refusingLists = refuse;
    }

    /// @param refuse whether [#reach] fails from now on
    synchronized void refuseCounts(boolean refuse) {
        refusingCounts = refuse;
    }

    /// Forgets every campaign, as a test that starts from an empty log needs:
    /// a campaign an earlier test made would look like one this test lost.
    synchronized void forgetCampaigns() {
        campaigns.clear();
        reports.clear();
        tests.clear();
    }

    /// @return every campaign's id, oldest first
    synchronized List<Long> campaignIds() {
        return List.copyOf(campaigns.keySet());
    }

    /// @return how many campaigns were made
    synchronized int campaignCount() {
        return campaigns.size();
    }

    /// @param campaignId a campaign
    /// @return what was handed over, or null
    synchronized @Nullable Campaign campaign(long campaignId) {
        return campaigns.get(campaignId);
    }

    /// @return every test send, as `<campaign id> <address>`
    synchronized List<String> tests() {
        return List.copyOf(tests);
    }

    /// @param campaignId a campaign
    /// @param report     what it reports from now on
    synchronized void markSent(long campaignId, CampaignReport report) {
        reports.put(campaignId, report);
    }

    /// @param refuse whether making a draft, listing campaigns, sending a test
    ///               and reading a report fail from now on, as when Brevo
    ///               cannot be reached, so a draft's answer is unknown
    synchronized void refuseCampaigns(boolean refuse) {
        refusing = refuse;
    }

    private void refuseIfAsked() {
        if (refusing) {
            throw new BrevoUnavailable("refusing campaign calls");
        }
    }
}
