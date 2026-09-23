package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.login.LoginSettings;

/// The pages around login by link, for members and administrators.
///
/// Only the GETs are here. Posting an address, posting a token and logging out are
/// Spring Security's filters, configured in `se.teaterihuskvarna.login`; these pages
/// hold the forms that post to them.
@Controller
public class LoginPagesController {

    private final LoginSettings settings;
    private final Copy copy;

    LoginPagesController(LoginSettings settings, Copy copy) {
        this.settings = settings;
        this.copy = copy;
    }

    /// @param failed present when a link did not work, as `?fel`
    /// @param model  receives which login page this is and whether to show the error
    /// @return the address form
    @GetMapping("/logga-in")
    public String memberForm(@RequestParam(name = "fel", required = false) @Nullable String failed, Model model) {
        return form(LoginPage.MEMBER, failed, model);
    }

    /// @param failed present when a link did not work, as `?fel`
    /// @param model  receives which login page this is and whether to show the error
    /// @return the address form
    @GetMapping("/admin/logga-in")
    public String administratorForm(
            @RequestParam(name = "fel", required = false) @Nullable String failed, Model model) {
        return form(LoginPage.ADMINISTRATOR, failed, model);
    }

    /// @param model receives which login page this is and how long a link works
    /// @return the page that says a link may be on its way
    @GetMapping("/logga-in/skickat")
    public String memberSent(Model model) {
        return sent(LoginPage.MEMBER, model);
    }

    /// @param model receives which login page this is and how long a link works
    /// @return the page that says a link may be on its way
    @GetMapping("/admin/logga-in/skickat")
    public String administratorSent(Model model) {
        return sent(LoginPage.ADMINISTRATOR, model);
    }

    /// @param token the token from the mailed link
    /// @param model receives which login page this is and the token
    /// @return the page with the button that logs in, or the address form with an error
    @GetMapping("/logga-in/lank")
    public String memberLink(@RequestParam(required = false) @Nullable String token, Model model) {
        return link(LoginPage.MEMBER, token, model);
    }

    /// @param token the token from the mailed link
    /// @param model receives which login page this is and the token
    /// @return the page with the button that logs in, or the address form with an error
    @GetMapping("/admin/logga-in/lank")
    public String administratorLink(@RequestParam(required = false) @Nullable String token, Model model) {
        return link(LoginPage.ADMINISTRATOR, token, model);
    }

    private static String form(LoginPage page, @Nullable String failed, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("failed", failed != null);
        return "login/form";
    }

    private String sent(LoginPage page, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("lifetime", copy.duration(settings.linkLifetime()));
        return "login/sent";
    }

    /// The link in the mail opens a page with a button, and only the button's POST
    /// logs in. A GET must never log in, because mail scanners and link previews
    /// follow every link in a message before the person does. A GET that consumed
    /// the token would spend the single use on a scanner, and the member would get
    /// a link that no longer works.
    private static String link(LoginPage page, @Nullable String token, Model model) {
        if (token == null || token.isBlank()) {
            return "redirect:" + page.path() + "?fel";
        }
        model.addAttribute("page", page);
        model.addAttribute("token", token);
        return "login/link";
    }
}
