package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/// What an editor writes in the Studio must reach the page as text, never as
/// markup, whatever it contains.
class PortableTextTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final PortableText portableText = new PortableText(new Images("proj", "prod"));

    @Test
    void textIsEscaped() {
        assertThat(render("[{\"_type\":\"block\",\"style\":\"normal\",\"children\":"
                + "[{\"_type\":\"span\",\"text\":\"<script>alert(1)</script> & \\\"x\\\"\",\"marks\":[]}]}]"))
                .isEqualTo("<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;x&quot;</p>");
    }

    @Test
    void aLinkToAScriptIsDroppedButItsTextStays() {
        assertThat(render("[{\"_type\":\"block\",\"style\":\"normal\",\"markDefs\":"
                + "[{\"_key\":\"l\",\"_type\":\"link\",\"href\":\"javascript:alert(1)\"}],"
                + "\"children\":[{\"_type\":\"span\",\"text\":\"klicka\",\"marks\":[\"l\"]}]}]"))
                .isEqualTo("<p>klicka</p>");
    }

    @Test
    void anUnknownStyleIsAParagraphAndAnUnknownTypeIsSkipped() {
        assertThat(render("[{\"_type\":\"block\",\"style\":\"<h9>\",\"children\":"
                + "[{\"_type\":\"span\",\"text\":\"a\",\"marks\":[]}]},{\"_type\":\"widget\"}]"))
                .isEqualTo("<p>a</p>");
    }

    @Test
    void nestedListsCloseInOrder() {
        String html = render("["
                + item("bullet", 1, "a") + "," + item("bullet", 2, "b") + "," + item("bullet", 1, "c") + ","
                + "{\"_type\":\"block\",\"style\":\"normal\",\"children\":"
                + "[{\"_type\":\"span\",\"text\":\"slut\",\"marks\":[]}]}]");

        assertThat(html).isEqualTo("<ul><li>a<ul><li>b</li></ul></li><li>c</li></ul><p>slut</p>");
    }

    /// In a preview, a click on a paragraph opens that paragraph in the Studio,
    /// so every block carries its own key. The value is escaped like any text.
    @Test
    void aPreviewMarksEachBlockWithItsKey() {
        JsonNode blocks = MAPPER.readTree("["
                + "{\"_type\":\"block\",\"_key\":\"p\",\"style\":\"normal\",\"children\":"
                + "[{\"_type\":\"span\",\"text\":\"a\",\"marks\":[]}]},"
                + "{\"_type\":\"block\",\"_key\":\"l\",\"style\":\"normal\",\"listItem\":\"bullet\",\"level\":1,"
                + "\"children\":[{\"_type\":\"span\",\"text\":\"b\",\"marks\":[]}]},"
                + "{\"_type\":\"image\",\"_key\":\"i\",\"alt\":\"c\","
                + "\"asset\":{\"_ref\":\"image-abc-10x20-png\"}}]");

        String html = portableText.html(blocks, key -> "k=\"" + key + "\"");

        assertThat(html).isEqualTo("<p data-sanity=\"k=&quot;p&quot;\">a</p>"
                + "<ul><li data-sanity=\"k=&quot;l&quot;\">b</li></ul>"
                + "<figure data-sanity=\"k=&quot;i&quot;\"><img src=\"https://cdn.sanity.io/images/proj/prod/"
                + "abc-10x20.png?w=10&amp;auto=format\" alt=\"c\" width=\"10\" height=\"20\""
                + " loading=\"lazy\"></figure>");
        assertThat(portableText.html(blocks)).doesNotContain("data-sanity");
    }

    @Test
    void nothingRendersAsNothing() {
        assertThat(portableText.html(null)).isEmpty();
    }

    private static String item(String kind, int level, String text) {
        return "{\"_type\":\"block\",\"style\":\"normal\",\"listItem\":\"" + kind + "\",\"level\":" + level
                + ",\"children\":[{\"_type\":\"span\",\"text\":\"" + text + "\",\"marks\":[]}]}";
    }

    private String render(String json) {
        JsonNode blocks = MAPPER.readTree(json);
        return portableText.html(blocks);
    }
}
