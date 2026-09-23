package se.teaterihuskvarna.login;

import java.time.Duration;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;

/// Spring's request for a link, with the [LoginBrowser] value the link is to be
/// bound to. Spring passes the request on to [HashedTokenService#generate]
/// untouched, so the value rides along with it.
final class BoundLinkRequest extends GenerateOneTimeTokenRequest {

    private final String browser;

    /// @param email    the address from the form
    /// @param lifetime how long the link works
    /// @param browser  the value of the browser's login cookie
    BoundLinkRequest(String email, Duration lifetime, String browser) {
        super(email, lifetime);
        this.browser = browser;
    }

    /// @return the value of the browser's login cookie
    String browser() {
        return browser;
    }
}
