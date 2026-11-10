package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import se.teaterihuskvarna.IntegrationTestSupport;

/// Only a handler that can show a form gives a visitor a session.
///
/// Reading the CSRF token stores it in the session and so creates one, a row
/// in `spring_session`. A handler reads it by declaring a [CsrfField], and it
/// has to declare one to show a page whose template takes one. A handler
/// whose pages have no form therefore writes no row, whatever else its
/// controller serves.
///
/// It goes by handler and not by page. `/inbjudan` without a token shows the
/// result page, which has no form, and still writes a row, because the same
/// handler shows the page with the accept button when there is a token.
///
/// Before, a list of controllers kept by hand decided who read the token, so
/// `/bli-medlem/skickat` wrote a row because the form at `/bli-medlem` is in
/// the same controller.
class PageSessionsIT extends IntegrationTestSupport {

    @Test
    void aPageWithoutAFormCreatesNoSession() throws Exception {
        for (String page : new String[] {"/", "/kalender", "/bli-medlem/skickat"}) {
            mockMvc.perform(get(page)).andExpect(status().isOk());

            assertThat(rowsIn("spring_session")).as("sessions after %s", page).isZero();
        }
    }

    @Test
    void aPageWithAFormCreatesOne() throws Exception {
        mockMvc.perform(get("/bli-medlem")).andExpect(status().isOk());

        assertThat(rowsIn("spring_session")).isOne();
    }
}
