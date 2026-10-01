package se.teaterihuskvarna.content;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

/// Turns Sanity's Portable Text, the JSON the Studio's rich text field stores,
/// into HTML: `docs/decisions/0021-portable-text-from-sanity.md`.
///
/// Every piece of text is escaped, and only what this class writes itself is
/// markup, so a template may print the result unescaped. What an editor can
/// produce is what `studio/schemaTypes/blockContent.ts` offers: paragraphs,
/// headings two to four, quotes, bullet and numbered lists, bold, italics,
/// links and images. Anything else in the JSON is left out rather than guessed
/// at.
///
/// A heading one becomes a heading two, because every page already has its own
/// heading one, the document's title.
final class PortableText {

    private static final Map<String, String> BLOCK_TAGS = Map.of(
            "normal", "p",
            "h1", "h2",
            "h2", "h2",
            "h3", "h3",
            "h4", "h4",
            "blockquote", "blockquote");

    private static final Map<String, String> DECORATOR_TAGS = Map.of(
            "strong", "strong",
            "em", "em",
            "underline", "u",
            "code", "code",
            "strike-through", "s");

    /// A link to anything else, `javascript:` above all, is dropped and its text
    /// kept.
    private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "mailto", "tel");

    private final Images images;

    PortableText(Images images) {
        this.images = images;
    }

    /// @param blocks a Portable Text array, or a missing or null node for none
    /// @return the HTML, empty when there is nothing to show
    String html(@Nullable JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        Deque<String> lists = new ArrayDeque<>();
        Deque<Integer> levels = new ArrayDeque<>();
        for (JsonNode block : blocks) {
            String listItem = Json.text(block, "listItem");
            if ("block".equals(Json.text(block, "_type")) && listItem != null) {
                listItem(out, lists, levels, block, listItem);
                continue;
            }
            closeLists(out, lists, levels, 0);
            switch (String.valueOf(Json.text(block, "_type"))) {
                case "block" -> block(out, block);
                case "image" -> image(out, block);
                default -> {
                    // A type the schema does not offer: left out.
                }
            }
        }
        closeLists(out, lists, levels, 0);
        return out.toString();
    }

    /// @param blocks a Portable Text array
    /// @return its text without markup, paragraphs separated by a blank line, for a summary or a mail
    static String plain(@Nullable JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            return "";
        }
        List<String> paragraphs = new ArrayList<>();
        for (JsonNode block : blocks) {
            if (!"block".equals(Json.text(block, "_type"))) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode child : block.path("children")) {
                String part = Json.text(child, "text");
                if (part != null) {
                    text.append(part);
                }
            }
            if (!text.isEmpty()) {
                paragraphs.add(text.toString());
            }
        }
        return String.join("\n\n", paragraphs);
    }

    private void listItem(StringBuilder out, Deque<String> lists, Deque<Integer> levels, JsonNode block,
            String listItem) {
        String tag = "number".equals(listItem) ? "ol" : "ul";
        int level = Math.max(1, block.path("level").asInt(1));
        while (!levels.isEmpty() && (top(levels) > level || top(levels) == level && !tag.equals(lists.peek()))) {
            out.append("</li></").append(lists.pop()).append('>');
            levels.pop();
        }
        if (!levels.isEmpty() && top(levels) == level) {
            out.append("</li>");
        } else {
            out.append('<').append(tag).append('>');
            lists.push(tag);
            levels.push(level);
        }
        out.append("<li>");
        inline(out, block);
    }

    private static int top(Deque<Integer> levels) {
        return levels.element();
    }

    private static void closeLists(StringBuilder out, Deque<String> lists, Deque<Integer> levels, int toLevel) {
        while (!levels.isEmpty() && top(levels) > toLevel) {
            out.append("</li></").append(lists.pop()).append('>');
            levels.pop();
        }
    }

    private void block(StringBuilder out, JsonNode block) {
        StringBuilder inner = new StringBuilder();
        inline(inner, block);
        if (inner.isEmpty()) {
            return;
        }
        String style = Json.text(block, "style");
        String tag = BLOCK_TAGS.getOrDefault(style == null ? "normal" : style, "p");
        out.append('<').append(tag).append('>').append(inner).append("</").append(tag).append('>');
    }

    /// Writes a block's spans with their marks. A mark that runs over several
    /// spans opens once: at each span, the marks it lacks close from the top of
    /// the stack down, and its new marks open longest-running first.
    private static void inline(StringBuilder out, JsonNode block) {
        Map<String, JsonNode> definitions = new HashMap<>();
        for (JsonNode definition : block.path("markDefs")) {
            String key = Json.text(definition, "_key");
            if (key != null) {
                definitions.put(key, definition);
            }
        }
        List<JsonNode> spans = new ArrayList<>();
        for (JsonNode child : block.path("children")) {
            if ("span".equals(Json.text(child, "_type")) || child.has("text")) {
                spans.add(child);
            }
        }
        List<String> open = new ArrayList<>();
        List<String> closers = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            List<String> marks = marks(spans.get(i), definitions);
            int keep = 0;
            while (keep < open.size() && marks.contains(open.get(keep))) {
                keep++;
            }
            for (int j = open.size() - 1; j >= keep; j--) {
                out.append(closers.remove(j));
                open.remove(j);
            }
            List<String> opening = new ArrayList<>(marks);
            opening.removeAll(open);
            int from = i;
            opening.sort((a, b) -> Integer.compare(runLength(spans, from, b, definitions),
                    runLength(spans, from, a, definitions)));
            for (String mark : opening) {
                String[] tags = tags(mark, definitions);
                out.append(tags[0]);
                open.add(mark);
                closers.add(tags[1]);
            }
            String text = Json.text(spans.get(i), "text");
            if (text != null) {
                out.append(escape(text).replace("\n", "<br>"));
            }
        }
        for (int j = closers.size() - 1; j >= 0; j--) {
            out.append(closers.get(j));
        }
    }

    /// The span's marks this class can write, in the order Sanity lists them.
    private static List<String> marks(JsonNode span, Map<String, JsonNode> definitions) {
        List<String> marks = new ArrayList<>();
        for (JsonNode mark : span.path("marks")) {
            String name = mark.isString() ? mark.stringValue() : null;
            if (name != null && (DECORATOR_TAGS.containsKey(name) || href(definitions.get(name)) != null)) {
                marks.add(name);
            }
        }
        return marks;
    }

    private static int runLength(List<JsonNode> spans, int from, String mark, Map<String, JsonNode> definitions) {
        int length = 0;
        while (from + length < spans.size() && marks(spans.get(from + length), definitions).contains(mark)) {
            length++;
        }
        return length;
    }

    private static String[] tags(String mark, Map<String, JsonNode> definitions) {
        String decorator = DECORATOR_TAGS.get(mark);
        if (decorator != null) {
            return new String[] {"<" + decorator + ">", "</" + decorator + ">"};
        }
        String address = Objects.requireNonNull(href(definitions.get(mark)));
        return new String[] {"<a href=\"" + escape(address) + "\">", "</a>"};
    }

    /// @return the link's address if it is one this class writes, otherwise null
    private static @Nullable String href(@Nullable JsonNode definition) {
        if (definition == null || !"link".equals(Json.text(definition, "_type"))) {
            return null;
        }
        String href = Json.text(definition, "href");
        if (href == null || href.isBlank()) {
            return null;
        }
        String trimmed = href.strip();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return trimmed;
        }
        int colon = trimmed.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String scheme = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
        return LINK_SCHEMES.contains(scheme) ? trimmed : null;
    }

    private void image(StringBuilder out, JsonNode block) {
        Image image = images.of(block);
        if (image == null) {
            return;
        }
        out.append("<figure><img src=\"").append(escape(image.url()))
                .append("\" alt=\"").append(escape(image.alt()))
                .append("\" width=\"").append(image.width())
                .append("\" height=\"").append(image.height())
                .append("\" loading=\"lazy\"></figure>");
    }

    /// Escapes only what HTML needs, `<>&"'`, and leaves å, ä and ö as they
    /// are: the page is UTF-8, and Spring's one-argument escape would write
    /// them as entities.
    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
