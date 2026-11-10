package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import se.teaterihuskvarna.login.DeviceService;
import se.teaterihuskvarna.login.SignedIn;

/// Keeps the [LoginCountdown] of a logged-in page on its request, for every
/// page in this package.
///
/// This is all the advice does. It used to put [Copy], the Studio's address
/// and the stale flag in every page's model under a name, for the template to
/// declare under the same name. A page now gets what it shows as the
/// arguments of its method on `Templates`, so the compiler checks both sides.
@ControllerAdvice(basePackageClasses = CountdownAdvice.class)
public class CountdownAdvice {

    private final DeviceService devices;

    CountdownAdvice(DeviceService devices) {
        this.devices = devices;
    }

    /// The countdown before the login ends, for the member's and the
    /// administrator's layout, which every logged-in page is drawn in. Only
    /// under `/medlem` for a member and `/admin` for an administrator: a
    /// public page shows no countdown, logged in or not.
    ///
    /// The token is read here and not in the template, for the reason
    /// [CsrfField] gives. Reading it creates no session, since a logged-in
    /// request has one.
    ///
    /// @param signedIn who is logged in, or null for nobody
    /// @param request  the request, which the layout finds the countdown on
    /// @param token    the request's token, which the countdown's script sends when it extends the login
    @ModelAttribute
    public void loginCountdown(@AuthenticationPrincipal @Nullable SignedIn signedIn, HttpServletRequest request,
            @Nullable CsrfToken token) {
        HttpSession session = request.getSession(false);
        if (signedIn == null || session == null) {
            return;
        }
        DeviceUrls urls = DeviceUrls.of(signedIn.kind());
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals(urls.page()) || path.startsWith(urls.page() + "/")) {
            new LoginCountdown(devices.status(session), urls, token == null ? "" : token.getToken())
                    .keepOn(request);
        }
    }
}
