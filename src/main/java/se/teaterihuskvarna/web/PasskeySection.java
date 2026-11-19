package se.teaterihuskvarna.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.login.LoginUrls;
import se.teaterihuskvarna.login.NoSuchPasskey;
import se.teaterihuskvarna.login.PasskeyDetails;
import se.teaterihuskvarna.login.PasskeyOffer;
import se.teaterihuskvarna.login.PasskeyService;
import se.teaterihuskvarna.login.SignedIn;

/// The passkey section of `/medlem` and `/admin`, which differ only in whose
/// passkeys they list and where the forms post. Adding a passkey is the
/// page's script talking to Spring's registration filter, so only listing and
/// removing come through here.
///
/// "Fråga inte igen" on the offer is remembered in a cookie, not the
/// database: a passkey belongs to one device, so a refusal on one should not
/// silence the offer on another. The cookie is `p=1`, scoped to the page's own
/// path, so only requests under `/medlem` or `/admin` carry it, and a member's
/// refusal leaves the administrator page alone. Chrome caps its lifetime at
/// 400 days, after which the offer comes back.
@Component
class PasskeySection {

    private static final String DECLINED = "p";
    private static final Duration DECLINED_FOR = Duration.ofDays(400);

    private final PasskeyService passkeys;
    private final Copy copy;

    PasskeySection(PasskeyService passkeys, Copy copy) {
        this.passkeys = passkeys;
        this.copy = copy;
    }

    /// @param signedIn whose passkeys to list
    /// @return their passkeys
    List<PasskeyDetails> list(SignedIn signedIn) {
        return passkeys.list(signedIn);
    }

    /// @param session  holds whether a link login just happened
    /// @param request  may carry the cookie "Fråga inte igen" left
    /// @return whether the page should offer a passkey; true at most once per link login
    boolean offer(HttpSession session, HttpServletRequest request) {
        boolean afterLinkLogin = PasskeyOffer.take(session);
        return afterLinkLogin && !declined(request);
    }

    /// Sets the cookie that stops the offer on this browser, for the kind of
    /// login `signedIn` is.
    ///
    /// @param signedIn who declined
    /// @param request  whether the connection is HTTPS, as the proxy reports it
    /// @param response receives the cookie
    void decline(SignedIn signedIn, HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(DECLINED, "1")
                .path(LoginUrls.of(signedIn.kind()).success())
                .maxAge(DECLINED_FOR)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static boolean declined(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (DECLINED.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    /// @param signedIn whose passkey to remove
    /// @param id       the passkey's id
    /// @param page     the redirect back to the page
    /// @return the redirect with the outcome
    Redirect remove(SignedIn signedIn, String id, Redirect page) {
        try {
            passkeys.remove(signedIn, id);
        } catch (NoSuchPasskey e) {
            return page.error(copy.text("passkey.error.noSuch"));
        }
        return page.notice(copy.text("passkey.removed"));
    }
}
