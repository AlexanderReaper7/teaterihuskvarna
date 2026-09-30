package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.teaterihuskvarna.IntegrationTestSupport;

/// The public pages and `/api/content`, over `content/fixture.json`, which the
/// test configuration reads instead of Sanity.
class ContentIT extends IntegrationTestSupport {

    @Autowired
    private Previews previews;

    @Test
    void theStartPageLeadsWithTheNextEvent() throws Exception {
        String page = html("/");

        assertThat(page).contains("Nästa evenemang", "Mitt i veckan", "Kulturnatten på torget", "Ny webbplats");
        assertThat(page.indexOf("Mitt i veckan")).isLessThan(page.indexOf("Kulturnatten på torget"));
        assertThat(page).doesNotContain("Vårshowen");
    }

    @Test
    void theCalendarFiltersBySeries() throws Exception {
        assertThat(html("/kalender")).contains("Mitt i veckan", "Kulturnatten på torget", "Läsning: Nya pjäser");
        assertThat(html("/kalender?serie=kulturnatt"))
                .contains("Kulturnatten på torget")
                .doesNotContain("Mitt i veckan", "Läsning: Nya pjäser");
    }

    @Test
    void anEventPageRendersItsPortableText() throws Exception {
        assertThat(html("/evenemang/mitt-i-veckan"))
                .contains("<h2>Om föreställningen</h2>", "<strong>90 minuter</strong>",
                        "<a href=\"https://example.test/biljetter\">hos arrangören</a>",
                        "<ul><li>Fika i pausen</li><li>Passar från tolv år</li></ul>");
    }

    @Test
    void newsDueInTheFutureStaysHidden() throws Exception {
        assertThat(html("/nyheter")).contains("Ny webbplats", "Audition").doesNotContain("inte är publicerad");
        assertThat(status("/nyheter/framtida")).isEqualTo(404);
        assertThat(html("/nyheter/ny-webbplats")).contains("Medlemmar loggar in under Mina sidor.");
    }

    @Test
    void theFixedPagesAndPartnersShow() throws Exception {
        for (String slug : ContentService.PAGES) {
            assertThat(status("/" + slug)).as(slug).isEqualTo(200);
        }
        assertThat(html("/styrelsen")).contains("<h1>Styrelsen</h1>");
        assertThat(html("/partners")).contains("Studieförbundet", "Jönköpings kommun");
    }

    @Test
    void anUnknownSlugIsNotFound() throws Exception {
        assertThat(status("/evenemang/finns-inte")).isEqualTo(404);
        assertThat(status("/api/content/events/finns-inte")).isEqualTo(404);
        assertThat(status("/api/content/pages/finns-inte")).isEqualTo(404);
    }

    @Test
    void theApiServesTheSameContent() throws Exception {
        assertThat(json("/api/content/next-event")).contains("\"slug\":\"mitt-i-veckan\"");
        assertThat(json("/api/content/events?serie=kulturnatt"))
                .contains("kulturnatten").doesNotContain("mitt-i-veckan");
        assertThat(json("/api/content/news")).contains("ny-webbplats").doesNotContain("framtida");
        assertThat(json("/api/content/series")).contains("Teatercafé");
        assertThat(json("/api/content/pages/kontakt")).contains("info@example.test");
        assertThat(json("/api/content/partners")).contains("Studieförbundet");
    }

    @Test
    void onlyTheStudioMayFrameAContentPage() throws Exception {
        MvcResult home = mockMvc.perform(get("/")).andReturn();
        assertThat(home.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo("frame-ancestors 'self' https://studio.example.test");
        assertThat(home.getResponse().getHeader("X-Frame-Options")).isNull();

        MvcResult login = mockMvc.perform(get("/logga-in")).andReturn();
        assertThat(login.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(login.getResponse().getHeader("Content-Security-Policy")).isNull();
    }

    @Test
    void theWebhookNeedsSanitysSignature() throws Exception {
        byte[] body = "{\"_id\":\"evenemang-kulturnatten\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = "1790000000000";
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(WebhookSignature.sign("test-webhook-secret", timestamp, body));

        assertThat(webhook(body, null)).isEqualTo(401);
        assertThat(webhook(body, "t=" + timestamp + ",v1=" + signature.replace('A', 'B') + "x")).isEqualTo(401);
        assertThat(webhook(body, "t=" + timestamp + ",v1=" + signature)).isEqualTo(204);
    }

    @Test
    void aWrongPreviewSecretShowsThePublishedSite() throws Exception {
        MvcResult start = mockMvc.perform(get("/forhandsgranska/start")
                .param("sanity-preview-secret", "gissat")
                .param("sanity-preview-pathname", "/nyheter"))
                .andReturn();

        assertThat(start.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(start.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void aForgedPreviewCookieShowsNoDrafts() throws Exception {
        String page = mockMvc.perform(get("/nyheter")
                .cookie(new Cookie(Previews.COOKIE, "99999999999.AAAA")))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).doesNotContain("Förhandsgranskning", "inte är publicerad");
        assertThat(previews.perspective("99999999999.AAAA")).isEqualTo(Perspective.PUBLISHED);
    }

    @Test
    void endingThePreviewClearsTheCookie() throws Exception {
        MvcResult end = mockMvc.perform(get("/forhandsgranska/avsluta")).andReturn();

        assertThat(end.getResponse().getHeader("Set-Cookie"))
                .startsWith(Previews.COOKIE + "=;")
                .contains("Max-Age=0", "SameSite=None", "Secure", "Partitioned");
    }

    private String html(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String json(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int status(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
    }

    private int webhook(byte[] body, @Nullable String signature) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/sanity/webhook")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("sanity-webhook-signature", signature);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }
}
