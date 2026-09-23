package se.teaterihuskvarna.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import gg.jte.springframework.boot.autoconfigure.JteAutoConfiguration;
import gg.jte.springframework.boot.autoconfigure.ServletJteAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/// Renders the start page without a database. Asserts the Swedish copy reaches
/// the HTML, which is what breaks if `messages_sv.properties` stops being read:
/// see `docs/decisions/0001-language-policy.md`.
@WebMvcTest(HomeController.class)
@Import(MessagesConfiguration.class) // the web slice excludes it
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
