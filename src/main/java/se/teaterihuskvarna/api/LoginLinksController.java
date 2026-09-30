package se.teaterihuskvarna.api;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.LinkOpening;
import se.teaterihuskvarna.login.LoginBrowser;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.LoginLinks;

/// What the page behind a mailed login link decides first: whether the link
/// works in this browser, only in the one that asked for it, or not at all.
/// Logging in itself is Spring Security's POST, the same for a page and a
/// client (`docs/decisions/0015-login-links-on-spring-security.md`).
///
/// `/api/login-links` permits anonymous requests so a link can be checked before login.
/// Never log the raw token or browser-binding cookie, including in proxy or request logs.
@RestController
public class LoginLinksController {

    private final LoginLinks links;

    LoginLinksController(LoginLinks links) {
        this.links = links;
    }

    /// Looks, and uses nothing up: the token still works afterwards.
    ///
    /// @param kind    `MEMBER` or `ADMINISTRATOR`
    /// @param token   the token in the link
    /// @param browser the login cookie the request for the link set
    /// @return `HERE`, `ELSEWHERE` or `UNUSABLE`
    @GetMapping("/api/login-links")
    public LinkStatus open(@RequestParam LoginKind kind, @RequestParam(required = false) @Nullable String token,
            @CookieValue(name = LoginBrowser.COOKIE, required = false) @Nullable String browser) {
        return new LinkStatus(links.open(kind, token, browser));
    }

    /// @param opening where the link works
    public record LinkStatus(LinkOpening opening) {
    }
}
