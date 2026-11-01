package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.SignedIn;

/// The menus mark the page being shown with `aria-current="page"`, and the
/// link to the section a page belongs to with `aria-current="true"`: the
/// header's menu on every page, and the member's and administrators' own menus.
class NavigationIT extends IntegrationTestSupport {

    @Test
    void aPublicPageMarksItsOwnLinkAndNoOther() throws Exception {
        String page = html("/kalender", null);

        assertThat(page).contains("<a href=\"/kalender\" aria-current=\"page\">", "<a href=\"/nyheter\">");
        assertThat(marks(page)).isEqualTo(1);
    }

    @Test
    void theMemberOverviewIsMarkedInBothMenus() throws Exception {
        String page = html("/medlem", member());

        assertThat(page.split("<a href=\"/medlem\" aria-current=\"page\">", -1)).hasSize(3);
        assertThat(marks(page)).isEqualTo(2);
    }

    @Test
    void aMemberPageMarksItselfAndMinaSidorButNotTheOverview() throws Exception {
        String page = html("/medlem/handlingar", member());

        assertThat(page)
                .contains("<a href=\"/medlem/handlingar\" aria-current=\"page\">")
                .contains("<a href=\"/medlem\" aria-current=\"true\">")
                .contains("<a href=\"/medlem\">");
        assertThat(marks(page)).isEqualTo(2);
    }

    @Test
    void aPageUnderAMenuLinkMarksThatLinkAsItsSection() throws Exception {
        long id = insertMember("Olle Lindqvist");

        String page = html("/admin/medlemmar/" + id, administrator());

        assertThat(page).contains("<a href=\"/admin/medlemmar\" aria-current=\"true\">", "<a href=\"/admin\">");
        assertThat(marks(page)).isEqualTo(1);
    }

    private RequestPostProcessor member() {
        return user(new SignedIn(LoginKind.MEMBER, insertAccount("Karin Holm", "karin@example.test"),
                "karin@example.test", "Karin Holm"));
    }

    private RequestPostProcessor administrator() {
        return user(new SignedIn(LoginKind.ADMINISTRATOR, firstAdministratorId(), firstAdministratorEmail,
                "Ada Admin"));
    }

    private static int marks(String page) {
        return page.split("aria-current=", -1).length - 1;
    }

    private String html(String path, @Nullable RequestPostProcessor login) throws Exception {
        MockHttpServletRequestBuilder request = get(path).accept(MediaType.TEXT_HTML);
        MvcResult result = mockMvc.perform(login == null ? request : request.with(login)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
