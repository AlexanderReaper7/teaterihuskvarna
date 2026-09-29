package se.teaterihuskvarna.mailing;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/// Brevo's v3 HTTP API, authenticated with the `api-key` header. Every call
/// follows Brevo's API reference as read on 2026-09-28; none has been made
/// against a real account.
final class HttpBrevo implements Brevo {

    /// Every status Brevo's documentation lists for a campaign, each with a
    /// Swedish name in `adminMailings.status.*`. Another one is treated as no
    /// answer, so the log keeps the last known status and the warning names it.
    private static final Set<String> STATUSES = Set.of("draft", "sent", "archive", "queued", "suspended",
            "in_process", "in_review", "cancelling", "cancelled");

    /// The contact attributes [Contact] fills, and their Brevo types. PAID_YEAR
    /// is text, not a number: Brevo ignores a value that does not match the
    /// type, so an empty string could not clear a number after a fee is undone.
    private static final Map<String, String> ATTRIBUTES = Map.of(
            "NAMN", "text", "PAID_YEAR", "text", "LAST_SHIFT", "date", "OFFERS", "text");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int SEGMENT_PAGE = 50;
    private static final int CONTACT_PAGE = 1000;

    private final RestClient client;
    private final BrevoSettings settings;
    private final long listId;
    private final long testListId;

    /// Whether the attributes exist in the account. Set after the first
    /// attempt to create them, which is safe to repeat.
    private volatile boolean attributesReady;

    HttpBrevo(BrevoSettings settings) {
        this(settings, RestClient.builder().requestFactory(timeouts()));
    }

    /// @param settings the settings, which must name every value this class needs
    /// @param builder  a client builder, which the tests bind to a mock server
    HttpBrevo(BrevoSettings settings, RestClient.Builder builder) {
        List<String> missing = new ArrayList<>();
        String key = settings.apiKey();
        if (key == null || key.isBlank()) {
            missing.add("BREVO_API_KEY");
        }
        Long list = settings.listId();
        if (list == null) {
            missing.add("BREVO_LIST_ID");
        }
        Long testList = settings.testListId();
        if (testList == null) {
            missing.add("BREVO_TEST_LIST_ID");
        }
        if (blank(settings.senderName())) {
            missing.add("BREVO_SENDER_NAME");
        }
        if (blank(settings.senderEmail())) {
            missing.add("BREVO_SENDER_EMAIL");
        }
        if (key == null || list == null || testList == null || !missing.isEmpty()) {
            throw new IllegalStateException("Brevo's HTTP API needs " + String.join(", ", missing));
        }
        this.settings = settings;
        this.listId = list;
        this.testListId = testList;
        this.client = builder
                .baseUrl(settings.apiUrl().toString())
                .defaultHeader("api-key", key)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /// Updates the contact found by the member's id, which also moves it to a
    /// new address, and creates it when Brevo has none. Creating goes with
    /// `updateEnabled`, so an existing contact with the address, such as an
    /// administrator's from a test send, becomes the member's.
    ///
    /// Brevo resubscribes a blocklisted contact whose address is changed. So
    /// the contact is read first, the address is sent only when it differs,
    /// and then with any blocklisting the contact has. That keeps an
    /// unsubscribe (R024); the application never blocklists a contact that was
    /// not. An unsubscribe that lands between the read and the update of an
    /// address change is lost, a window of milliseconds on a change only an
    /// administrator makes.
    @Override
    public void saveContact(Contact contact) {
        ensureAttributes();
        JsonNode existing;
        try {
            existing = client.get()
                    .uri("/contacts/{id}?identifierType=ext_id", contact.memberId())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound ignored) {
            existing = null;
        } catch (RestClientException e) {
            throw new BrevoUnavailable("Brevo did not find member " + contact.memberId() + ": " + e.getMessage(), e);
        }
        if (existing == null) {
            post("/contacts", Map.of(
                    "email", contact.email(),
                    "ext_id", String.valueOf(contact.memberId()),
                    "attributes", attributes(contact),
                    "listIds", List.of(listId),
                    "updateEnabled", true));
            return;
        }
        Map<String, @Nullable Object> attributes = attributes(contact);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("attributes", attributes);
        body.put("listIds", List.of(listId));
        String current = existing.path("email").isString() ? existing.path("email").stringValue() : "";
        if (!current.equalsIgnoreCase(contact.email())) {
            attributes.put("EMAIL", contact.email());
            if (existing.path("emailBlacklisted").asBoolean(false)) {
                body.put("emailBlacklisted", true);
            }
        }
        try {
            client.put()
                    .uri("/contacts/{id}?identifierType=ext_id", contact.memberId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new BrevoUnavailable("Brevo refused to update member " + contact.memberId() + ": "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void deleteContact(long memberId) {
        try {
            client.delete()
                    .uri("/contacts/{id}?identifierType=ext_id", memberId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Nothing to delete.
        } catch (RestClientException e) {
            throw new BrevoUnavailable("Brevo refused to delete member " + memberId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<Segment> segments() {
        List<Segment> segments = new ArrayList<>();
        for (int offset = 0;; offset += SEGMENT_PAGE) {
            JsonNode body;
            try {
                body = client.get()
                        .uri("/contacts/segments?limit={limit}&offset={offset}&sort=asc", SEGMENT_PAGE, offset)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientException e) {
                throw new BrevoUnavailable("Brevo did not list its segments: " + e.getMessage(), e);
            }
            JsonNode page = body == null ? null : body.path("segments");
            if (page == null || !page.isArray()) {
                return sorted(segments);
            }
            for (JsonNode segment : page) {
                if (segment.path("id").canConvertToLong() && segment.path("segmentName").isString()) {
                    segments.add(new Segment(segment.path("id").asLong(), segment.path("segmentName").stringValue()));
                }
            }
            if (page.size() < SEGMENT_PAGE) {
                return sorted(segments);
            }
        }
    }

    /// Reads every contact in the segment, or on the list, a thousand at a
    /// time, Brevo's largest page, and counts them by whether they are on the
    /// members' list and have not unsubscribed.
    @Override
    public MailingReach reach(@Nullable Long segmentId) {
        long members = 0;
        long nonMembers = 0;
        for (int offset = 0;; offset += CONTACT_PAGE) {
            JsonNode body;
            try {
                body = client.get()
                        .uri(segmentId == null
                                ? "/contacts?listIds=" + listId + "&limit={limit}&offset={offset}"
                                : "/contacts?segmentId=" + segmentId + "&limit={limit}&offset={offset}",
                                CONTACT_PAGE, offset)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientException e) {
                throw new BrevoUnavailable("Brevo did not list the contacts to count: " + e.getMessage(), e);
            }
            JsonNode page = body == null ? null : body.path("contacts");
            if (page == null || !page.isArray()) {
                // An answer without the list is no answer: counting it as
                // the end would pass a segment nobody checked.
                throw new BrevoUnavailable("Brevo listed contacts without a contacts array");
            }
            for (JsonNode contact : page) {
                boolean onList = false;
                for (JsonNode id : contact.path("listIds")) {
                    onList |= id.asLong() == listId;
                }
                if (!onList) {
                    nonMembers++;
                } else if (!contact.path("emailBlacklisted").asBoolean(false)) {
                    members++;
                }
            }
            if (page.size() < CONTACT_PAGE) {
                return new MailingReach(members, nonMembers);
            }
        }
    }

    @Override
    public long createDraft(Campaign campaign) {
        Long segment = campaign.segmentId();
        Map<String, Object> recipients = segment == null
                ? Map.of("listIds", List.of(listId))
                : Map.of("segmentIds", List.of(segment));
        Map<String, Object> body = Map.of(
                "name", campaign.name(),
                "subject", campaign.subject(),
                "sender", Map.of("name", settings.senderName(), "email", settings.senderEmail()),
                "htmlContent", campaign.html(),
                "recipients", recipients);
        return id(post("/emailCampaigns", body), "campaign");
    }

    @Override
    public void sendTest(long campaignId, String email) {
        post("/contacts", Map.of("email", email, "listIds", List.of(testListId), "updateEnabled", true));
        post("/emailCampaigns/" + campaignId + "/sendTest", Map.of("emailTo", List.of(email)));
    }

    @Override
    public CampaignReport report(long campaignId) {
        JsonNode body;
        try {
            body = client.get()
                    .uri("/emailCampaigns/{id}?statistics=globalStats&excludeHtmlContent=true", campaignId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new BrevoUnavailable("Brevo did not report campaign " + campaignId + ": " + e.getMessage(), e);
        }
        if (body == null || !body.path("status").isString()) {
            throw new BrevoUnavailable("Brevo reported campaign " + campaignId + " without a status");
        }
        String status = body.path("status").stringValue();
        if (!STATUSES.contains(status)) {
            throw new BrevoUnavailable("Brevo reported campaign " + campaignId + " in a status the log does not know: "
                    + status);
        }
        JsonNode stats = body.path("statistics").path("globalStats");
        return new CampaignReport(status, instant(body.path("sentDate")),
                stats.path("sent").asLong(0), stats.path("delivered").asLong(0),
                stats.path("uniqueViews").asLong(0), stats.path("unsubscriptions").asLong(0),
                stats.path("hardBounces").asLong(0));
    }

    /// Creates each attribute [Contact] needs. Brevo answers 400 for one that
    /// exists, which is the usual case after the first run, so a 400 counts
    /// as done. A real refusal shows up as the contact update failing.
    private void ensureAttributes() {
        if (attributesReady) {
            return;
        }
        for (Map.Entry<String, String> attribute : ATTRIBUTES.entrySet()) {
            try {
                client.post()
                        .uri("/contacts/attributes/normal/{name}", attribute.getKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("type", attribute.getValue()))
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException.BadRequest ignored) {
                // The attribute exists.
            } catch (RestClientException e) {
                throw new BrevoUnavailable("Brevo refused attribute " + attribute.getKey() + ": " + e.getMessage(),
                        e);
            }
        }
        attributesReady = true;
    }

    /// An empty text clears a text attribute that no longer applies, such as
    /// PAID_YEAR after the only payment is undone. A date cannot hold an empty
    /// text, so LAST_SHIFT is cleared with null, which Brevo's reference does
    /// not describe; it happens only when a started shift is deleted.
    private static Map<String, @Nullable Object> attributes(Contact contact) {
        Map<String, @Nullable Object> attributes = new LinkedHashMap<>();
        attributes.put("NAMN", contact.name());
        Integer paid = contact.paidYear();
        attributes.put("PAID_YEAR", paid == null ? "" : String.valueOf(paid));
        LocalDate lastShift = contact.lastShift();
        attributes.put("LAST_SHIFT", lastShift == null ? null : lastShift.toString());
        attributes.put("OFFERS", contact.offers());
        return attributes;
    }

    private static List<Segment> sorted(List<Segment> segments) {
        return segments.stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }

    private static JdkClientHttpRequestFactory timeouts() {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requests.setReadTimeout(TIMEOUT);
        return requests;
    }

    private @Nullable JsonNode post(String path, Map<String, ?> body) {
        try {
            return client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new BrevoUnavailable("Brevo refused POST " + path + ": " + e.getMessage(), e);
        }
    }

    private static long id(@Nullable JsonNode body, String what) {
        if (body == null || !body.path("id").canConvertToLong()) {
            throw new BrevoUnavailable("Brevo created a " + what + " without an id");
        }
        return body.path("id").asLong();
    }

    private static @Nullable Instant instant(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        try {
            return Instant.parse(node.stringValue());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
