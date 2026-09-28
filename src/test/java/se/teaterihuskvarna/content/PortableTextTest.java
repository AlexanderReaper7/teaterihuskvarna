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
