package se.teaterihuskvarna.web;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/// Placeholder for the public start page. P1 requires the next event at the top,
/// which needs content from Sanity; this renders the frame that will hold it.
///
/// Visible Swedish comes from `messages_sv.properties`, never from the template:
/// see `docs/decisions/0001-language-policy.md`.
@Controller
public class HomeController {

    private static final Locale SWEDISH = Locale.of("sv", "SE");

    private final MessageSource messages;

    HomeController(MessageSource messages) {
        this.messages = messages;
    }

    /// @param model receives the page title and intro, already in Swedish
    /// @return the name of the JTE template to render
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", text("home.title"));
        model.addAttribute("intro", text("home.intro"));
        return "home";
    }

    private String text(String key) {
        return messages.getMessage(key, null, SWEDISH);
    }
}
