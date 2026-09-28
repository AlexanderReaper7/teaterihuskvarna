package se.teaterihuskvarna.mailing;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/// Brevo's v3 HTTP API, authenticated with the `api-key` header.
final class HttpBrevo implements Brevo {

    /// Every status Brevo's documentation lists for a campaign, each with a
    /// Swedish name in `adminMailings.status.*`. Another one is treated as no
    /// answer, so the log keeps the last known status and the warning names it.
    private static final Set<String> STATUSES = Set.of("draft", "sent", "archive", "queued", "suspended",
            "in_process", "in_review", "cancelling", "cancelled");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final RestClient client;
    private final BrevoSettings settings;
    private final long folderId;
    private final long testListId;

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
        Long folder = settings.folderId();
        if (folder == null) {
            missing.add("BREVO_FOLDER_ID");
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
        if (key == null || folder == null || testList == null || !missing.isEmpty()) {
            throw new IllegalStateException("Brevo's HTTP API needs " + String.join(", ", missing));
        }
        this.settings = settings;
        this.folderId = folder;
        this.testListId = testList;
        this.client = builder
                .baseUrl(settings.apiUrl().toString())
                .defaultHeader("api-key", key)
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public long createList(String name) {
        return id(post("/contacts/lists", Map.of("name", name, "folderId", folderId)), "list");
    }

    @Override
    public void addContact(String email, long listId) {
        post("/contacts", Map.of("email", email, "listIds", List.of(listId), "updateEnabled", true));
    }

    @Override
    public long createDraft(Campaign campaign) {
        Map<String, Object> body = Map.of(
                "name", campaign.name(),
                "subject", campaign.subject(),
                "sender", Map.of("name", settings.senderName(), "email", settings.senderEmail()),
                "htmlContent", campaign.html(),
                "recipients", Map.of("listIds", List.of(campaign.listId())));
        return id(post("/emailCampaigns", body), "campaign");
    }

    @Override
    public void sendTest(long campaignId, String email) {
        addContact(email, testListId);
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
