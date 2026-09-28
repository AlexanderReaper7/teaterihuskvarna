package se.teaterihuskvarna.mailing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/// Keeps every call in memory and sends nothing, for development and the
/// tests. A campaign reports `draft` until [#markSent] is called, the way a
/// real one does until an administrator sends it in Brevo.
final class FakeBrevo implements Brevo {

    private final AtomicLong ids = new AtomicLong(100);
    private final Map<Long, String> lists = new LinkedHashMap<>();
    private final Map<Long, Set<String>> members = new LinkedHashMap<>();
    private final Map<Long, Campaign> campaigns = new LinkedHashMap<>();
    private final Map<Long, CampaignReport> reports = new LinkedHashMap<>();
    private final List<String> tests = new ArrayList<>();

    @Override
    public synchronized long createList(String name) {
        long id = ids.incrementAndGet();
        lists.put(id, name);
        members.put(id, new LinkedHashSet<>());
        return id;
    }

    @Override
    public synchronized void addContact(String email, long listId) {
        members.computeIfAbsent(listId, id -> new LinkedHashSet<>()).add(email);
    }

    @Override
    public synchronized long createDraft(Campaign campaign) {
        if (!lists.containsKey(campaign.listId())) {
            throw new BrevoUnavailable("no list " + campaign.listId());
        }
        long id = ids.incrementAndGet();
        campaigns.put(id, campaign);
        reports.put(id, new CampaignReport("draft", null, 0, 0, 0, 0, 0));
        return id;
    }

    @Override
    public synchronized void sendTest(long campaignId, String email) {
        if (!campaigns.containsKey(campaignId)) {
            throw new BrevoUnavailable("no campaign " + campaignId);
        }
        tests.add(campaignId + " " + email);
    }

    @Override
    public synchronized CampaignReport report(long campaignId) {
        CampaignReport report = reports.get(campaignId);
        if (report == null) {
            throw new BrevoUnavailable("no campaign " + campaignId);
        }
        return report;
    }

    /// @param listId a list
    /// @return the addresses on it
    synchronized Set<String> contacts(long listId) {
        return Set.copyOf(members.getOrDefault(listId, Set.of()));
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
}
