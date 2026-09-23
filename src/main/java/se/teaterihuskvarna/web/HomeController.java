package se.teaterihuskvarna.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// Placeholder for the public start page. P1 requires the next event at the top,
/// which needs content from Sanity; this renders the frame that will hold it.
///
/// Visible Swedish comes from `messages_sv.properties` through [Copy], never from
/// the template: see `docs/decisions/0001-language-policy.md`.
///
/// Not under the `dev` profile, where [DevelopmentIndexController] answers `/`.
@Controller
@Profile("!dev")
public class HomeController {

    /// @return the name of the JTE template to render
    @GetMapping("/")
    public String home() {
        return "home";
    }
}
