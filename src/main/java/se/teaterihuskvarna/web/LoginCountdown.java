package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import se.teaterihuskvarna.login.LoginStatus;

/// What the countdown before a login ends needs on a page: `tag/loginCountdown.jte`
/// and `/js/session.js`. Every page a member or an administrator sees logged in
/// has it, because a login can end on any of them, and on a page with a form
/// that costs what was typed.
///
/// The countdown is therefore in the member's and the administrator's layout,
/// and a layout sees only what the page calling it passes on. Rather than every
/// page passing this on, [CountdownAdvice] keeps it on the request before the page
/// renders, and the layouts ask [#current], as they ask [Navigation] for the
/// path.
///
/// @param status when the login ends
/// @param urls   where the script asks and extends, and the login page it links to once the login has ended
/// @param csrf   the token the script sends when it extends. The countdown carries its own, because a
///               page such as `/medlem/handlingar` has no form to borrow one from
public record LoginCountdown(LoginStatus status, DeviceUrls urls, String csrf) {

    private static final String ATTRIBUTE = LoginCountdown.class.getName();

    /// @return the countdown of the page being rendered, or null when nobody is logged in on it
    public static @Nullable LoginCountdown current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(ATTRIBUTE) instanceof LoginCountdown countdown) {
            return countdown;
        }
        return null;
    }

    /// @param request the request whose page [#current] is then asked about
    void keepOn(HttpServletRequest request) {
        request.setAttribute(ATTRIBUTE, this);
    }
}
