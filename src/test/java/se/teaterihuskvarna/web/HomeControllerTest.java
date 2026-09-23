package se.teaterihuskvarna.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import gg.jte.springframework.boot.autoconfigure.ServletJteAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/// Renders the start page without a database. Asserts the Swedish copy reaches
/// the HTML, which is what breaks if `messages_sv.properties` stops being read:
/// see `docs/decisions/0001-language-policy.md`. Asserts on rendered text only,
/// so it does not care how the controller gets the copy.
///
/// Runs without the security filters. With Spring Security on the classpath the
/// slice switches on Boot's default security, which answers 401 to everything,
/// because the slice does not load `SecurityConfiguration`. Importing that class
/// is not an option: its beans need `JdbcClient`, the token store and the
/// directories, which is the whole application minus the web layer. The access
/// rules are tested where they run for real, against the real chains, in
/// `LoginIT`, which also checks that `/` is public.
@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(MessagesConfiguration.class) // the web slice excludes it, and it declares Copy
@ImportAutoConfiguration({JteAutoConfiguration.class, ServletJteAutoConfiguration.class}) // and JTE
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startPageRendersSwedishCopy() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Teater i Huskvarna")))
                .andExpect(content().string(containsString("Webbplatsen byggs om")));
    }
}
