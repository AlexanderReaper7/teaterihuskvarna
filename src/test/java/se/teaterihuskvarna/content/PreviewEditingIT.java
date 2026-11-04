package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.teaterihuskvarna.IntegrationTestSupport;

/// R009 with the Studio's Presentation tool: a preview page loads Sanity's
/// visual-editing script and marks what each element shows with a
/// `data-sanity` attribute, and a visitor's page does neither.
///
/// The attributes are written by hand in the templates, so a misspelt field
/// would give an edit button that opens nothing. Every attribute's field is
/// therefore checked against the schema in `studio/schemaTypes/`, by the names
/// the schema files declare. The check reads names at any depth in the file,
/// so it catches a field no schema has, not one moved to the wrong level.
class PreviewEditingIT extends IntegrationTestSupport {

    /// Every content page the fixture fills, with the lists the pages show.
    private static final List<String> PAGES = List.of("/", "/kalender", "/evenemang/mitt-i-veckan",
            "/evenemang/kulturnatten", "/nyheter", "/nyheter/ny-webbplats", "/om-foreningen", "/styrelsen",
            "/produktioner", "/ludde-priser", "/kontakt", "/partners");

    private static final Pattern ATTRIBUTE = Pattern.compile("data-sanity=\"([^\"]*)\"");
    private static final Pattern FIELD = Pattern.compile("(?:name: '|imageField\\(')([A-Za-z]+)'");

    @MockitoSpyBean
    private ContentSource source;

    @Autowired
    private Previews previews;

    @Test
    void aPreviewPageConnectsToPresentationAndNamesItsFields() throws Exception {
        String pass = pass();
        Set<String> documents = new HashSet<>();
        for (String path : PAGES) {
            String page = html(path, pass);
            assertThat(page).as(path).contains("<script type=\"module\" src=\"/js/visual-editing.js\"></script>",
                    "<link rel=\"stylesheet\" href=\"/css/visual-editing.css\">");
            List<Attribute> attributes = attributes(page);
            assertThat(attributes).as(path).isNotEmpty();
            for (Attribute attribute : attributes) {
                // The test's SANITY_STUDIO_URL ends in a slash, which the overlays throw on.
                assertThat(attribute.base()).as(path).isEqualTo("https%3A%2F%2Fstudio.example.test%2Fstudio");
                assertThat(schemaFields(attribute.type())).as(path + " " + attribute)
                        .contains(attribute.path().split("[:.]")[0]);
                documents.add(attribute.id());
            }
        }
        // Each type the fixture has reaches some page: a series through its events.
        assertThat(documents).contains("serie-teatercafe", "evenemang-mitt-i-veckan", "nyhet-ny-webbplats",
                "sida-kontakt", "partner-kommun", "produktion-tusen", "styrelse-ordforande", "ludde-barn");
    }

    @Test
    void richTextMarksEachParagraph() throws Exception {
        String page = html("/evenemang/mitt-i-veckan", pass());

        assertThat(page).contains("<h2 data-sanity=\"id=evenemang-mitt-i-veckan;type=evenemang;path=beskrivning:h;");
    }

    @Test
    void aVisitorsPageLoadsNoEditingAndNamesNoFields() throws Exception {
        for (String path : PAGES) {
            String page = html(path, null);
            assertThat(page).as(path).doesNotContain("visual-editing", "data-sanity");
        }
    }

    private String pass() {
        doReturn(true).when(source).previewSecretValid("hemlig");
        return previews.start("hemlig", "192.0.2.1").orElseThrow().value();
    }

    private String html(String path, @Nullable String pass) throws Exception {
        MockHttpServletRequestBuilder request = get(path).accept(MediaType.TEXT_HTML);
        if (pass != null) {
            request.cookie(new Cookie(Previews.COOKIE, pass));
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /// One `data-sanity` value, split the way the overlays split it.
    private record Attribute(String id, String type, String path, String base) {
    }

    private static List<Attribute> attributes(String page) {
        List<Attribute> attributes = new ArrayList<>();
        Matcher matcher = ATTRIBUTE.matcher(page);
        while (matcher.find()) {
            Map<String, String> parts = new HashMap<>();
            for (String part : matcher.group(1).split(";")) {
                String[] pair = part.split("=", 2);
                parts.put(pair[0], pair.length > 1 ? pair[1] : "");
            }
            assertThat(parts).as(matcher.group(1)).containsOnlyKeys("id", "type", "path", "base");
            attributes.add(new Attribute(parts.getOrDefault("id", ""), parts.getOrDefault("type", ""),
                    parts.getOrDefault("path", ""), parts.getOrDefault("base", "")));
        }
        return attributes;
    }

    private static Set<String> schemaFields(String type) throws IOException {
        Path schema = Path.of("studio", "schemaTypes", type + ".ts");
        assertThat(schema).as("the schema of " + type).exists();
        Set<String> fields = new HashSet<>();
        Matcher matcher = FIELD.matcher(Files.readString(schema, StandardCharsets.UTF_8));
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }
}
