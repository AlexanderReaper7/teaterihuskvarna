package se.teaterihuskvarna.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/// Documents from a JSON file in the jar, keyed by type, in the form
/// [SanityContentSource] returns them. For development without a Sanity project
/// and for the e2e suite, which needs the same content every run.
///
/// A date written as `@+7d 19:00` is that many days from today at that time in
/// Sweden, so the fixture's events stay upcoming. Drafts are the published
/// documents: a fixture has no editor.
final class FixtureContentSource implements ContentSource {

    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    private static final Pattern RELATIVE = Pattern.compile("@([+-]\\d+)d (\\d{2}:\\d{2})");

    private final JsonNode fixture;
    private final Clock clock;

    FixtureContentSource(String resource, ObjectMapper mapper, Clock clock) {
        this.clock = clock;
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            this.fixture = mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the content fixture " + resource, e);
        }
    }

    @Override
    public List<JsonNode> documents(String type, Perspective perspective) {
        List<JsonNode> documents = new ArrayList<>();
        for (JsonNode document : fixture.path(type)) {
            documents.add(resolveDates(document.deepCopy()));
        }
        return documents;
    }

    /// A fixture has no Studio, so no secret is ever valid.
    @Override
    public boolean previewSecretValid(String secret) {
        return false;
    }

    private JsonNode resolveDates(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> field : List.copyOf(object.properties())) {
                object.set(field.getKey(), resolveDates(field.getValue()));
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, resolveDates(array.get(i)));
            }
        } else if (node.isString()) {
            Matcher relative = RELATIVE.matcher(node.stringValue());
            if (relative.matches()) {
                LocalDate day = LocalDate.now(clock.withZone(SWEDEN)).plusDays(Long.parseLong(relative.group(1)));
                return StringNode.valueOf(day.atTime(LocalTime.parse(relative.group(2)))
                        .atZone(SWEDEN).toInstant().toString());
            }
        }
        return node;
    }
}
