package se.teaterihuskvarna.content;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/// Sanity's HTTP query API, with GROQ.
///
/// The live API rather than `apicdn.sanity.io`: the CDN keeps an answer for a
/// while after a publish, which would add to the minute R008 allows, and this
/// application already keeps its own copy for that minute. The API version is
/// a date Sanity freezes the API's behaviour at.
final class SanityContentSource implements ContentSource {

    private static final String API_VERSION = "v2025-02-19";

    /// Resolves the references a page needs, so one query per type is enough.
    /// A type without an entry comes back as stored.
    private static final Map<String, String> PROJECTIONS = Map.of(
            "evenemang", "{..., \"serie\": serie->{\"title\": title, \"slug\": slug}}");

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;
    private final ContentSettings settings;
    private final ObjectMapper mapper;

    SanityContentSource(ContentSettings settings, ObjectMapper mapper) {
        this(settings, mapper, requestClient());
    }

    SanityContentSource(ContentSettings settings, ObjectMapper mapper, RestClient.Builder configured) {
        this.settings = settings;
        this.mapper = mapper;
        // Sanity sends raw deflate, but Spring's decoder expects a zlib header.
        configured.defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip");
        String token = settings.token();
        if (token != null && !token.isBlank()) {
            configured.defaultHeader("Authorization", "Bearer " + token);
        }
        this.client = configured.build();
    }

    private static RestClient.Builder requestClient() {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requests.setReadTimeout(TIMEOUT);
        return RestClient.builder().requestFactory(requests);
    }

    @Override
    public List<JsonNode> documents(String type, Perspective perspective) {
        JsonNode result = query("*[_type == $value]" + PROJECTIONS.getOrDefault(type, ""), type,
                perspective == Perspective.DRAFTS ? "drafts" : "published");
        if (!result.isArray()) {
            throw new ContentUnavailable("Sanity answered a query for " + type + " without a list");
        }
        List<JsonNode> documents = new ArrayList<>();
        result.forEach(documents::add);
        return documents;
    }

    /// The same query `@sanity/preview-url-secret` makes in its
    /// `validatePreviewUrl`, with the same hour. The secret documents are
    /// system documents, readable only with a token and the raw perspective.
    @Override
    public boolean previewSecretValid(String secret) {
        String token = settings.token();
        if (token == null || token.isBlank()) {
            return false;
        }
        JsonNode result = query("*[_type == \"sanity.previewUrlSecret\" && secret == $value"
                + " && dateTime(_updatedAt) > dateTime(now()) - 3600][0]._id", secret, "raw");
        return result.isString();
    }

    /// Runs a query with one parameter, `$value`, which the address carries as a JSON literal.
    private JsonNode query(String query, String value, String perspective) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://{project}.api.sanity.io/" + API_VERSION + "/data/query/{dataset}")
                .queryParam("query", "{query}")
                .queryParam("$value", "{value}")
                .queryParam("perspective", perspective)
                .encode()
                .buildAndExpand(settings.projectId(), settings.dataset(), query, mapper.writeValueAsString(value))
                .toUri();
        JsonNode body;
        try {
            body = client.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ContentUnavailable("Sanity query failed: " + e.getMessage(), e);
        }
        if (body == null || !body.has("result")) {
            throw new ContentUnavailable("Sanity answered a query without a result");
        }
        return body.path("result");
    }
}
