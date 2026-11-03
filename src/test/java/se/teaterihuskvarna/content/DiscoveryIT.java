package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Discovery uses published public content and the configured site URL, never the caller's host.
class DiscoveryIT extends IntegrationTestSupport {

    @Test
    void theSitemapIsXmlAndIncludesPublishedPagesWithoutPrivateOrFutureContent() throws Exception {
        String xml = mockMvc.perform(get("/sitemap.xml").header("Host", "attacker.example"))
                .andExpect(status().isOk())
                .andExpect(xpath("/s:urlset/s:url/s:loc", Map.of("s", "http://www.sitemaps.org/schemas/sitemap/0.9"))
                        .exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(xml).contains("/evenemang/kulturnatten</loc>", "/nyheter/ny-webbplats</loc>",
                "/kontakt</loc>", "/bli-medlem</loc>")
                .doesNotContain("attacker.example", "/medlem", "/admin", "framtida", "token=", "drafts.");
    }

    @Test
    void theApiExposesTheSamePublicPaths() throws Exception {
        String paths = mockMvc.perform(get("/api/content/public-paths"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(paths).contains("\"/\"", "\"/kontakt\"", "\"/nyheter/ny-webbplats\"")
                .doesNotContain("framtida", "/admin", "/medlem");
    }

    @Test
    void robotsPointsToTheConfiguredSitemapAndExcludesAccountAndTokenPages() throws Exception {
        String robots = mockMvc.perform(get("/robots.txt").header("Host", "attacker.example"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(robots).contains("User-agent: *", "Disallow: /admin", "Disallow: /medlem",
                "Disallow: /forhandsgranska/", "Disallow: /inbjudan", "Sitemap: " + siteUrl + "/sitemap.xml")
                .doesNotContain("attacker.example");
    }

    @Test
    void publicPagesHaveTheirOwnDescriptionsAndAbsoluteSharingUrls() throws Exception {
        String news = html("/nyheter/ny-webbplats");
        assertThat(news).contains("<meta name=\"description\"", "<meta property=\"og:title\" content=\"Ny webbplats\">",
                "<meta property=\"og:type\" content=\"article\">", "<meta property=\"og:image\" content=\"http",
                "<meta property=\"og:locale\" content=\"sv_SE\">")
                .doesNotContain("attacker.example", "name=\"robots\" content=\"noindex");
        assertThat(html("/kalender?serie=kulturnatt"))
                .contains("<link rel=\"canonical\" href=\"" + siteUrl + "/kalender\">");
        assertThat(html("/bli-medlem")).contains("<meta name=\"description\"");
    }

    @Test
    void loginPagesAskCrawlersNotToIndexThem() throws Exception {
        assertThat(html("/logga-in")).contains("<meta name=\"robots\" content=\"noindex, nofollow\">");
    }

    @Test
    void theMembershipFormPrecedesTheBenefitsAndKeepsTheFeeVisible() throws Exception {
        String page = html("/bli-medlem");
        assertThat(page).contains("50 kr", "100 kr").doesNotContain("teaterverksamheten, Huskvarna");
        int form = page.indexOf("id=\"fullName\"");
        assertThat(form).isNotNegative().isLessThan(page.indexOf("<h2>Som medlem</h2>"));
    }

    private String html(String path) throws Exception {
        return mockMvc.perform(get(path).accept(MediaType.TEXT_HTML).header("Host", "attacker.example"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
