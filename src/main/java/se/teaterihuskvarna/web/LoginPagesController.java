package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;
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
    private final Templates templates;

    LoginPagesController(LoginSettings settings, LoginLinks links, Copy copy, Templates templates) {
        this.settings = settings;
        this.links = links;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param failed   present when a link did not work, as `?fel`
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the address form
    @GetMapping("/logga-in")
    public View memberForm(@RequestParam(name = "fel", required = false) @Nullable String failed, CsrfField csrf,
            Feedback feedback) {
        return form(LoginPage.MEMBER, failed, csrf, feedback);
    }

    /// @param failed   present when a link did not work, as `?fel`
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the address form
    @GetMapping("/admin/logga-in")
    public View administratorForm(@RequestParam(name = "fel", required = false) @Nullable String failed,
            CsrfField csrf, Feedback feedback) {
        return form(LoginPage.ADMINISTRATOR, failed, csrf, feedback);
    }

    /// @param failed present when a code did not work, as `?fel`
    /// @param csrf   the hidden field of the page's form
    /// @return the page that says a link may be on its way, with the form for the code
    @GetMapping("/logga-in/skickat")
    public View memberSent(@RequestParam(name = "fel", required = false) @Nullable String failed, CsrfField csrf) {
        return sent(LoginPage.MEMBER, failed, csrf);
    }

    /// @param failed present when a code did not work, as `?fel`
    /// @param csrf   the hidden field of the page's form
    /// @return the page that says a link may be on its way, with the form for the code
    @GetMapping("/admin/logga-in/skickat")
    public View administratorSent(@RequestParam(name = "fel", required = false) @Nullable String failed,
            CsrfField csrf) {
        return sent(LoginPage.ADMINISTRATOR, failed, csrf);
    }

    /// @param token   the token from the mailed link
    /// @param browser the browser's [LoginBrowser] cookie, if it has one
    /// @param csrf    the hidden field of the page's form
    /// @return the page with the button that logs in, the page saying to open the link where it was asked for,
    ///         or the address form with an error
    @GetMapping("/logga-in/lank")
    public View memberLink(
            @RequestParam(required = false) @Nullable String token,
            @CookieValue(name = LoginBrowser.COOKIE, required = false) @Nullable String browser,
            CsrfField csrf) {
        return link(LoginPage.MEMBER, token, browser, csrf);
    }

    /// @param token   the token from the mailed link
    /// @param browser the browser's [LoginBrowser] cookie, if it has one
    /// @param csrf    the hidden field of the page's form
    /// @return the page with the button that logs in, the page saying to open the link where it was asked for,
    ///         or the address form with an error
    @GetMapping("/admin/logga-in/lank")
    public View administratorLink(
            @RequestParam(required = false) @Nullable String token,
            @CookieValue(name = LoginBrowser.COOKIE, required = false) @Nullable String browser,
            CsrfField csrf) {
        return link(LoginPage.ADMINISTRATOR, token, browser, csrf);
    }

    private View form(LoginPage page, @Nullable String failed, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.loginForm(copy, csrf, feedback, page, failed != null));
    }

    private View sent(LoginPage page, @Nullable String failed, CsrfField csrf) {
        return HtmlPage.of(templates.loginSent(copy, csrf, page, copy.duration(settings.linkLifetime()),
                failed != null));
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
    private View link(LoginPage page, @Nullable String token, @Nullable String browser, CsrfField csrf) {
        return switch (links.open(page.kind(), token, browser)) {
            case HERE -> HtmlPage.of(templates.loginLink(copy, csrf, page, token));
            case ELSEWHERE -> HtmlPage.of(templates.loginElsewhere(copy, page));
            case UNUSABLE -> Redirect.to(page.failurePath());
        };
    }
}
