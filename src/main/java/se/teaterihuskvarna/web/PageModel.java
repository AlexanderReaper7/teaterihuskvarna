package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.content.ContentSettings;
import se.teaterihuskvarna.login.DeviceService;
import se.teaterihuskvarna.login.SignedIn;

/// Puts [Copy] in the model of every page in this package, as `copy`, and
/// whether the page was sent back because its form had gone stale, as `stale`.
/// Keeps the [LoginCountdown] of a logged-in page on its request.
///
/// Extra model attributes cost nothing in a template. The JTE starter hands the
/// whole Spring model to the template as a map, and the precompiled template reads
/// only the names it declares with `@param`, so an attribute nobody declared is
/// ignored. A declared name with nothing in the model arrives as null, and nothing
/// checks that at build time: the model is the one part of a JTE page the compiler
/// cannot see.
@ControllerAdvice(basePackageClasses = PageModel.class)
public class PageModel {

    private final Copy copy;
    private final ContentSettings content;
    private final DeviceService devices;

    PageModel(Copy copy, ContentSettings content, DeviceService devices) {
        this.copy = copy;
        this.content = content;
        this.devices = devices;
    }

    /// @return the configured content editor, or null when none is connected
    @ModelAttribute("studioUrl")
    public @Nullable String studioUrl() {
        String url = content.studioUrl();
        return url == null || url.isBlank() ? null : url;
    }

    /// @return the Swedish copy, for the template to read by key
    @ModelAttribute("copy")
    public Copy copy() {
        return copy;
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

    /// `se.teaterihuskvarna.login.RefusedRequests` adds `gammal` to the query
    /// when it sends a form whose session is gone back to its page.
    ///
    /// @param stale the `gammal` parameter, present or not
    /// @return whether to ask the person to send the form again
    @ModelAttribute("stale")
    public boolean stale(@RequestParam(name = "gammal", required = false) @Nullable String stale) {
        return stale != null;
    }
}
