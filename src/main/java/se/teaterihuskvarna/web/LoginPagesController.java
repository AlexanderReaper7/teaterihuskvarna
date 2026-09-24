package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.login.LinkOpening;
import se.teaterihuskvarna.login.LoginBrowser;
import se.teaterihuskvarna.login.LoginLinks;
import se.teaterihuskvarna.login.LoginSettings;

/// The pages around login by link, for members and administrators.
///
/// Only the GETs are here. Posting an address, posting a token or a code and logging out are
/// Spring Security's filters, configured in `se.teaterihuskvarna.login`; these pages
/// hold the forms that post to them.
@Controller
public class LoginPagesController {

    private final LoginSettings settings;
    private final LoginLinks links;
    private final Copy copy;

    LoginPagesController(LoginSettings settings, LoginLinks links, Copy copy) {
        this.settings = settings;
        this.links = links;
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

    /// @param failed present when a code did not work, as `?fel`
    /// @param model  receives which login page this is, how long a link works and whether to show the error
    /// @return the page that says a link may be on its way, with the form for the code
    @GetMapping("/logga-in/skickat")
    public String memberSent(@RequestParam(name = "fel", required = false) @Nullable String failed, Model model) {
        return sent(LoginPage.MEMBER, failed, model);
    }

    /// @param failed present when a code did not work, as `?fel`
    /// @param model  receives which login page this is, how long a link works and whether to show the error
    /// @return the page that says a link may be on its way, with the form for the code
    @GetMapping("/admin/logga-in/skickat")
    public String administratorSent(
            @RequestParam(name = "fel", required = false) @Nullable String failed, Model model) {
        return sent(LoginPage.ADMINISTRATOR, failed, model);
    }

    /// @param token   the token from the mailed link
    /// @param browser the browser's [LoginBrowser] cookie, if it has one
    /// @param model   receives which login page this is and the token
    /// @return the page with the button that logs in, the page saying to open the link where it was asked for,
    ///         or the address form with an error
    @GetMapping("/logga-in/lank")
    public String memberLink(
            @RequestParam(required = false) @Nullable String token,
            @CookieValue(name = LoginBrowser.COOKIE, required = false) @Nullable String browser,
            Model model) {
        return link(LoginPage.MEMBER, token, browser, model);
    }

    /// @param token   the token from the mailed link
    /// @param browser the browser's [LoginBrowser] cookie, if it has one
    /// @param model   receives which login page this is and the token
    /// @return the page with the button that logs in, the page saying to open the link where it was asked for,
    ///         or the address form with an error
    @GetMapping("/admin/logga-in/lank")
    public String administratorLink(
            @RequestParam(required = false) @Nullable String token,
            @CookieValue(name = LoginBrowser.COOKIE, required = false) @Nullable String browser,
            Model model) {
        return link(LoginPage.ADMINISTRATOR, token, browser, model);
    }

    private static String form(LoginPage page, @Nullable String failed, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("failed", failed != null);
        return "login/form";
    }

    private String sent(LoginPage page, @Nullable String failed, Model model) {
        model.addAttribute("page", page);
        model.addAttribute("lifetime", copy.duration(settings.linkLifetime()));
        model.addAttribute("failed", failed != null);
        return "login/sent";
    }

    /// The link in the mail opens a page with a button, and only the button's POST
    /// logs in. A GET must never log in, because mail scanners and link previews
    /// follow every link in a message before the person does. A GET that consumed
    /// the token would spend the single use on a scanner, and the member would get
    /// a link that no longer works.
    ///
    /// A link opened in another browser than the one that asked for it gets a
    /// page saying so, rather than a button that would fail. The token stays
    /// usable where it was asked for, and so does the code.
    private String link(LoginPage page, @Nullable String token, @Nullable String browser, Model model) {
        LinkOpening opening = links.open(page.kind(), token, browser);
        model.addAttribute("page", page);
        return switch (opening) {
            case HERE -> {
                model.addAttribute("token", token);
                yield "login/link";
            }
            case ELSEWHERE -> "login/elsewhere";
            case UNUSABLE -> "redirect:" + page.failurePath();
        };
    }
}
