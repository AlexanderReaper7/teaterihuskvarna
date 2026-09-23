package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serial;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.util.StringUtils;

/// What a POST to the link path carries: the token from a link or the code
/// from a mail, and the browser's [LoginBrowser] value. Spring's provider
/// hands this to [HashedTokenService#consume] as it is.
final class BoundLogin extends OneTimeTokenAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final @Nullable String code;
    private final @Nullable String browser;

    private BoundLogin(String token, @Nullable String code, @Nullable String browser) {
        super(token);
        this.code = code;
        this.browser = browser;
    }

    /// Reads the form field `token`, or `code` when there is no token, as the
    /// filter's converter. Spaces in a code are dropped, so "123 456" works.
    ///
    /// @param request a POST to the link path
    /// @return the login to check, or null if neither field has a value, which
    ///         makes Spring's filter fail the login
    static @Nullable BoundLogin from(HttpServletRequest request) {
        String token = request.getParameter("token");
        String code = request.getParameter("code");
        String browser = LoginBrowser.read(request);
        if (StringUtils.hasText(token)) {
            return new BoundLogin(token, null, browser);
        }
        if (StringUtils.hasText(code)) {
            return new BoundLogin("", StringUtils.trimAllWhitespace(code), browser);
        }
        return null;
    }

    /// @return the code from the mail, or null for a login by link
    @Nullable String code() {
        return code;
    }

    /// @return the browser's login cookie, or null if it sent none
    @Nullable String browser() {
        return browser;
    }
}
