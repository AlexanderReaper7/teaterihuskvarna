package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.RequestDispatcher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.teaterihuskvarna.IntegrationTestSupport;

/// The page Boot's error controller renders for a browser, called the way the
/// servlet container calls it after `sendError`. MockMvc does not follow that
/// forward itself, so the tests of who gets which status, in `LoginIT`, do not
/// see this page.
class ErrorPageIT extends IntegrationTestSupport {

    @Test
    void aMissingPageSaysSo() throws Exception {
        MvcResult page = error(404);

        assertThat(page.getResponse().getStatus()).isEqualTo(404);
        assertThat(page.getResponse().getContentAsString())
                .contains("<h1>Sidan finns inte</h1>", "Till startsidan")
                .doesNotContain("Whitelabel");
    }

    @Test
    void anyOtherErrorSaysSomethingWentWrong() throws Exception {
        MvcResult page = error(500);

        assertThat(page.getResponse().getStatus()).isEqualTo(500);
        assertThat(page.getResponse().getContentAsString())
                .contains("<h1>Något gick fel</h1>", "Till startsidan")
                .doesNotContain("Sidan finns inte", "Whitelabel");
    }

    /// A form refused as stale goes back to the page it was on. The mailing's
    /// preview is drawn by a POST, and a GET of its address is read as a
    /// mailing whose id is not a number, which is 400. A fee form shown again
    /// with what was wrong in it has an address only a POST is mapped to.
    @Test
    void aStaleFormThatCameBackToNoPageGoesToThePageAbove() throws Exception {
        assertThat(stale(400, "/admin/utskick/forhandsgranska").getResponse().getRedirectedUrl())
                .isEqualTo("/admin/utskick?gammal");
        assertThat(stale(405, "/admin/medlemmar/5/avgift").getResponse().getRedirectedUrl())
                .isEqualTo("/admin/medlemmar/5?gammal");
        assertThat(stale(404, "/medlem/hushall/7/").getResponse().getRedirectedUrl())
                .isEqualTo("/medlem/hushall?gammal");
    }

    /// Without the mark it is an ordinary error. So is an address directly
    /// under the start page, a failure that is not about the address, and a
    /// path a browser could read as another host.
    @Test
    void anyOtherErrorStaysAnErrorPage() throws Exception {
        assertThat(error(400, "/admin/utskick/forhandsgranska", false).getResponse().getStatus()).isEqualTo(400);
        for (MvcResult page : List.of(stale(404, "/finns-inte"), stale(500, "/admin/utskick/forhandsgranska"),
                stale(404, "//elsewhere.test/x"), stale(404, "/a\\elsewhere.test/x"))) {
            assertThat(page.getResponse().getRedirectedUrl()).isNull();
            assertThat(page.getResponse().getContentAsString()).contains("Till startsidan");
        }
        MvcResult posted = mockMvc.perform(post("/error").with(csrf()).param("gammal", "")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/utskick/forhandsgranska")
                .accept(MediaType.TEXT_HTML))
                .andReturn();
        assertThat(posted.getResponse().getRedirectedUrl()).isNull();
    }

    private MvcResult error(int status) throws Exception {
        return error(status, "/finns-inte", false);
    }

    private MvcResult stale(int status, String asked) throws Exception {
        return error(status, asked, true);
    }

    private MvcResult error(int status, String asked, boolean stale) throws Exception {
        MockHttpServletRequestBuilder request = get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, asked)
                .accept(MediaType.TEXT_HTML);
        return mockMvc.perform(stale ? request.param("gammal", "") : request).andReturn();
    }
}
