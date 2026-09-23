package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
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

    private MvcResult error(int status) throws Exception {
        return mockMvc.perform(get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/finns-inte")
                .accept(MediaType.TEXT_HTML))
                .andReturn();
    }
}
