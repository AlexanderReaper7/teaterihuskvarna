package se.teaterihuskvarna.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.LoginUrls;
import se.teaterihuskvarna.login.NoSuchPasskey;
import se.teaterihuskvarna.login.PasskeyOffer;
import se.teaterihuskvarna.login.PasskeyService;
import se.teaterihuskvarna.login.PasskeyUrls;
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

    /// @param model    receives `passkeys`, `passkeyUrls` and `offerPasskey`
    /// @param signedIn whose passkeys to list
    /// @param offer    whether to ask if they want to add one, as [se.teaterihuskvarna.login.PasskeyOffer] says
    void addTo(Model model, SignedIn signedIn, boolean offer) {
        model.addAttribute("passkeys", passkeys.list(signedIn));
        model.addAttribute("passkeyUrls", PasskeyUrls.of(signedIn.kind()));
        model.addAttribute("offerPasskey", offer);
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

    /// @param signedIn   whose passkey to remove
    /// @param id         the passkey's id
    /// @param redirected receives the outcome, shown after the redirect
    void remove(SignedIn signedIn, String id, RedirectAttributes redirected) {
        try {
            passkeys.remove(signedIn, id);
        } catch (NoSuchPasskey e) {
            redirected.addFlashAttribute("error", copy.text("passkey.error.noSuch"));
            return;
        }
        redirected.addFlashAttribute("notice", copy.text("passkey.removed"));
    }
}
