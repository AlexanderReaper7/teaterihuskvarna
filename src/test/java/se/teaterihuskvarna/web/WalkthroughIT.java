package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.HtmlUtils;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// The walkthroughs, as the server sends them: `tag/adminTour.jte` for the
/// administrators, `tag/memberTour.jte` for the members, and [Walkthrough].
/// What the script does with them is in `e2e/tests/tour.spec.ts` and
/// `e2e/tests/member-tour.spec.ts`.
class WalkthroughIT extends IntegrationTestSupport {

    private static final Pattern STEP = Pattern.compile("<div data-tour-step\\b[^>]*>");
    private static final Cookie RUNNING = new Cookie(Walkthrough.COOKIE, "1");

    @Test
    void aPageAskedForWithoutTheCookieHoldsTheStartButtonAndNoStep() throws Exception {
        String page = html("/admin", null, administrator());

        assertThat(page)
                .contains("<div data-tour-start=\"/admin\" hidden>",
                        "<script src=\"/js/tour.js\" data-root=\"/admin\" defer>")
                .doesNotContain("id=\"tour\"", "data-tour-step");
    }

    @Test
    void aPageAskedForWithTheCookieHoldsTheStepsOfItsOwnSection() throws Exception {
        List<String> administrators = steps(html("/admin/handlingar", RUNNING, administrator()));
        assertThat(administrators).hasSizeGreaterThan(20).allMatch(step -> !step.contains("\"/medlem"));

        String page = html("/medlem/handlingar", RUNNING, member());
        assertThat(page).contains("<script src=\"/js/tour.js\" data-root=\"/medlem\" defer>");
        assertThat(steps(page)).hasSizeGreaterThan(10).allMatch(step -> !step.contains("/admin"));
    }

    @Test
    void aMemberPageAskedForWithoutTheCookieHoldsTheStartButtonAndNoStep() throws Exception {
        assertThat(html("/medlem", null, member())).contains("<div data-tour-start=\"/medlem\" hidden>")
                .doesNotContain("id=\"tour\"", "data-tour-step");
    }

    /// A step's link is written out as text, and nothing else ties it to a
    /// page: `TemplateLinksTest` reads `href`, not `data-href`. A link that
    /// leads nowhere, or to a page the step is not on, would leave the
    /// reader on a page whose card says the step is somewhere else.
    @Test
    void everyStepsLinkLeadsToAPageThatStepIsOn() throws Exception {
        assertThat(linksFollowed("/admin", administrator()))
                .as("links followed, so the test cannot pass by finding none").isGreaterThan(10);
        assertThat(linksFollowed("/medlem", member())).isGreaterThan(8);
    }

    private int linksFollowed(String first, RequestPostProcessor reader) throws Exception {
        List<String> wrong = new ArrayList<>();
        int followed = 0;
        for (String step : steps(html(first, RUNNING, reader))) {
            String href = attribute(step, "data-href");
            if (href == null) {
                continue;
            }
            followed++;
            URI link = URI.create(href);
            String path = attribute(step, "data-path");
            String search = attribute(step, "data-search");
            if (path != null && !Pattern.compile(path).matcher(link.getPath()).find()) {
                wrong.add("%s does not match its own path, %s".formatted(href, path));
            }
            if (search != null && (link.getQuery() == null || !link.getQuery().contains(search))) {
                wrong.add("%s does not hold its own query, %s".formatted(href, search));
            }
            if (path == null && attribute(step, "data-on") == null) {
                wrong.add("%s names neither a path nor an element, so every page would be its page".formatted(href));
            }
            int status = mockMvc.perform(get(href).accept(MediaType.TEXT_HTML).with(reader)).andReturn()
                    .getResponse().getStatus();
            if (status != 200) {
                wrong.add("%s answers %d".formatted(href, status));
            }
        }
        assertThat(wrong).isEmpty();
        return followed;
    }

    private static List<String> steps(String page) {
        List<String> steps = new ArrayList<>();
        Matcher step = STEP.matcher(page);
        while (step.find()) {
            steps.add(step.group());
        }
        return steps;
    }

    private static @Nullable String attribute(String tag, String name) {
        Matcher value = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(tag);
        return value.find() ? HtmlUtils.htmlUnescape(value.group(1)) : null;
    }

    private String html(String path, @Nullable Cookie cookie, RequestPostProcessor reader) throws Exception {
        MockHttpServletRequestBuilder request = get(path).accept(MediaType.TEXT_HTML).with(reader);
        MvcResult result = mockMvc.perform(cookie == null ? request : request.cookie(cookie)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private RequestPostProcessor member() {
        long member = insertAccount("Karin Holm", "karin@example.test");
        return user(new SignedIn(LoginKind.MEMBER, member, "karin@example.test", "Karin Holm"));
    }

    private RequestPostProcessor administrator() {
        return user(new SignedIn(LoginKind.ADMINISTRATOR, firstAdministratorId(), firstAdministratorEmail,
                "Ada Admin"));
    }
}
