package se.teaterihuskvarna.content;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/// Reads fields from Sanity's documents, where any field may be missing, null
/// or of another type than the schema says, since the schema lives in the
/// Studio and the dataset keeps whatever an older schema wrote.
final class Json {

    private Json() {
    }

    /// @param node  an object
    /// @param field the field's name
    /// @return the field's text, or null if it is missing, not text, or blank
    static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString()) {
            return null;
        }
        String text = value.stringValue();
        return text.isBlank() ? null : text;
    }

    /// @param node  an object
    /// @param field the field's name
    /// @return the field as a moment, or null if it is missing or not an ISO 8601 date and time
    static @Nullable Instant instant(JsonNode node, String field) {
        String text = text(node, field);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /// @param node an object with a Sanity slug field
    /// @return `slug.current`, or null
    static @Nullable String slug(JsonNode node) {
        return text(node.path("slug"), "current");
    }
}
