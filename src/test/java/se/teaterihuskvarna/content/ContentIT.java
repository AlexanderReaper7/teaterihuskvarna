package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.teaterihuskvarna.IntegrationTestSupport;

/// The public pages and `/api/content`, over `content/fixture.json`, which the
/// test configuration reads instead of Sanity.
class ContentIT extends IntegrationTestSupport {

    /// The most bytes the webhook reads, written out here and not taken from
    /// the controller, so that a changed limit has to change this test.
    private static final int WEBHOOK_BODY_LIMIT = 65_536;

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
    void theListPagesShowTheirDocumentsInOrderAndSkipIncompleteOnes() throws Exception {
        String productions = html("/produktioner");
        assertThat(productions).contains("Panik på kliniken", "Bara tusen korta år",
                "<span>1994</span>–<span>1997</span>", "Läs nyhetsbrevet",
                "Sommarens fars i Huskvarna.");
        assertThat(productions.indexOf("Panik på kliniken")).isLessThan(productions.indexOf("Bara tusen korta år"));
        assertThat(productions.indexOf("Bara tusen korta år")).isLessThan(productions.indexOf("Läs nyhetsbrevet"));

        String board = html("/styrelsen");
        assertThat(board).contains("Marianne Exempel", "Ordförande", "Folkrörelsearkivet", "Ulla Exempel")
                .doesNotContain("Utan Roll");
        assertThat(board.indexOf("Marianne Exempel")).isLessThan(board.indexOf("Ulla Exempel"));

        assertThat(html("/ludde-priser")).contains("Barngrupperna", "2022", "En Julsaga").doesNotContain("Utan år");
        assertThat(html("/kontakt")).doesNotContain("class=\"cards\"");
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
        assertThat(json("/api/content/productions")).contains("\"firstYear\":1994", "\"lastYear\":1997")
                .doesNotContain("\"firstYear\":2020");
        assertThat(json("/api/content/board")).contains("Marianne Exempel").doesNotContain("Utan Roll");
        assertThat(json("/api/content/ludde-prizes")).contains("\"year\":2022").doesNotContain("Utan år");
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

    /// Anyone can call the webhook, so a body that says it is over the limit
    /// is refused before a byte of it is read. The body is signed, to show
    /// that its size alone refuses it.
    @Test
    void theWebhookRefusesABodyOverTheLimitByItsLengthUnread() throws Exception {
        WebhookRequest request = signedWebhook(WEBHOOK_BODY_LIMIT + 1, true);

        assertThat(mockMvc.perform(context -> request.in(context)).andReturn().getResponse().getStatus())
                .isEqualTo(413);
        assertThat(request.bytesRead()).isZero();
    }

    /// A chunked body has no length to go by. The application reads one byte
    /// past the limit, enough to know, and leaves the rest.
    @Test
    void theWebhookRefusesAChunkedBodyOverTheLimitWithoutReadingItAll() throws Exception {
        WebhookRequest request = signedWebhook(1024 * 1024, false);

        assertThat(mockMvc.perform(context -> request.in(context)).andReturn().getResponse().getStatus())
                .isEqualTo(413);
        assertThat(request.bytesRead()).isEqualTo(WEBHOOK_BODY_LIMIT + 1);
    }

    @Test
    void theWebhookTakesASignedBodyAtTheLimitWithOrWithoutALength() throws Exception {
        for (boolean lengthDeclared : new boolean[] {true, false}) {
            WebhookRequest request = signedWebhook(WEBHOOK_BODY_LIMIT, lengthDeclared);

            assertThat(mockMvc.perform(context -> request.in(context)).andReturn().getResponse().getStatus())
                    .as("length declared: %s", lengthDeclared).isEqualTo(204);
        }
    }

    /// The signature covers the bytes as sent, also when they arrive chunked.
    @Test
    void theWebhookRefusesAChunkedBodyWithAnotherBodysSignature() throws Exception {
        WebhookRequest request = signedWebhook(100, false);
        request.removeHeader("sanity-webhook-signature");
        request.addHeader("sanity-webhook-signature", signature(new byte[100]));

        assertThat(mockMvc.perform(context -> request.in(context)).andReturn().getResponse().getStatus())
                .isEqualTo(401);
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

    /// @param size           the body's length in bytes
    /// @param lengthDeclared whether the request has a `Content-Length`, as Sanity's has; without one it is chunked
    /// @return a webhook request with Sanity's signature for its body
    private static WebhookRequest signedWebhook(int size, boolean lengthDeclared) {
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) 'a');
        WebhookRequest request = new WebhookRequest(body, lengthDeclared);
        request.addHeader("sanity-webhook-signature", signature(body));
        return request;
    }

    private static String signature(byte[] body) {
        String timestamp = "1790000000000";
        return "t=" + timestamp + ",v1=" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(WebhookSignature.sign("test-webhook-secret", timestamp, body));
    }

    /// A webhook request whose body is a stream that counts what the
    /// application reads from it. MockMvc's own builder always declares the
    /// length and hands the body over as one array.
    private static final class WebhookRequest extends MockHttpServletRequest {

        private final byte[] body;
        private final boolean lengthDeclared;
        private int bytesRead;

        WebhookRequest(byte[] body, boolean lengthDeclared) {
            super("POST", "/api/sanity/webhook");
            this.body = body.clone();
            this.lengthDeclared = lengthDeclared;
            setContentType(MediaType.APPLICATION_JSON_VALUE);
        }

        /// @return this request, since MockMvc asks a builder for a request in its servlet context
        WebhookRequest in(ServletContext context) {
            assertThat(context).isNotNull();
            return this;
        }

        int bytesRead() {
            return bytesRead;
        }

        @Override
        public int getContentLength() {
            return lengthDeclared ? body.length : -1;
        }

        @Override
        public long getContentLengthLong() {
            return getContentLength();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new DelegatingServletInputStream(new FilterInputStream(new ByteArrayInputStream(body)) {

                @Override
                public int read() throws IOException {
                    int value = super.read();
                    bytesRead += value < 0 ? 0 : 1;
                    return value;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = super.read(buffer, offset, length);
                    bytesRead += Math.max(count, 0);
                    return count;
                }
            });
        }
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
