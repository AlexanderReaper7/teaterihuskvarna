package se.teaterihuskvarna.login;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;

/// The cookie that ties a login link to the browser that asked for it. Asking
/// for a link sets it, and the link and its code log in only where it is sent
/// back. Without it, anyone could ask for a link to their own address and send
/// it to someone else, who would then be logged in as the sender without
/// noticing: `docs/decisions/0015-login-links-on-spring-security.md`.
///
/// The value is a random token like the one in a link, and the database keeps
/// its hash. A browser that asks again keeps its value, so an older link from
/// the same browser still works.
public final class LoginBrowser {

    /// The cookie's name, for a page that reads it with `@CookieValue`.
    public static final String COOKIE = "login-browser";

    private static final String ATTRIBUTE = LoginBrowser.class.getName();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private LoginBrowser() {
    }

    /// Sets the cookie on the response, keeping the browser's value if it sent
    /// one, and remembers the value for [#read] later in the same request.
    ///
    /// `Path` is the login page, so the browser sends the cookie to the login
    /// pages of that kind and nowhere else. It lasts as long as a link does,
    /// counted from the newest request. `SameSite=Lax`, because the link arrives
    /// from a mail program, and `Strict` would leave the cookie off that request.
    ///
    /// @param request  may carry an earlier value
    /// @param response receives the cookie
    /// @param urls     the paths of the login asked for
    /// @param lifetime how long a link works
    /// @return the value, now set
    static String issue(HttpServletRequest request, HttpServletResponse response, LoginUrls urls,
            Duration lifetime) {
        String sent = fromCookie(request);
        String value = sent != null ? sent : Tokens.newToken();
        request.setAttribute(ATTRIBUTE, value);
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .path(urls.page())
                .maxAge(lifetime)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
        return value;
    }

    /// @param request a request to a login page
    /// @return the value [#issue] set earlier in this request, or else the one the browser sent,
    ///         or null if there is neither
    static @Nullable String read(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof String issued ? issued : fromCookie(request);
    }

    /// Checks the format as well, so a value made up by hand is replaced rather
    /// than kept and hashed.
    ///
    /// @param value what a page read from the cookie
    /// @return whether it has the format [#issue] gives it
    static boolean isWellFormed(@Nullable String value) {
        return value != null && TOKEN.matcher(value).matches();
    }

    private static @Nullable String fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(LoginBrowser::isWellFormed)
                .findFirst()
                .orElse(null);
    }
}
