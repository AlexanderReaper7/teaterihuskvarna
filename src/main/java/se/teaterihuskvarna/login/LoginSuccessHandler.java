package se.teaterihuskvarna.login;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import tools.jackson.databind.json.JsonMapper;

/// Gives a new login its lifetime and its device name, then sends the browser
/// on. One instance per [LoginKind] and way of logging in, built by
/// [SecurityConfiguration] and [PasskeyLogin].
///
/// A login ends a fixed time after it starts, 30 days for a member and 8 hours
/// for an administrator, and [LoginExpiryFilter] enforces the end stored in
/// [LoginSession]. The session's max inactive interval is set to the same
/// lifetime, which only matters for a session nobody uses again: Spring
/// Session's cleanup deletes the row that long after its last request, which
/// is never before the stored end.
///
/// The request attribute tells Spring Session's `DefaultCookieSerializer` to
/// give the cookie a max age, so it survives the browser closing; without it
/// the cookie is a session cookie and the login ends with the browser, whatever
/// the server allows. The long max age does not extend anything, since a cookie
/// for an ended session finds nothing. It also means extending a login needs no
/// new cookie.
///
/// Spring Boot sets the serializer's remember-me attribute to
/// `SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR` whenever Spring
/// Security is on the classpath, and [SecurityConfiguration] sets it again so
/// this class does not depend on that.
final class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final Duration lifetime;
    private final DeviceNames devices;
    private final boolean offerPasskey;
    private final AuthenticationSuccessHandler then;

    private LoginSuccessHandler(Duration lifetime, DeviceNames devices, boolean offerPasskey,
            AuthenticationSuccessHandler then) {
        this.lifetime = lifetime;
        this.devices = devices;
        this.offerPasskey = offerPasskey;
        this.then = then;
    }

    /// A link login redirects, and the page it lands on offers a passkey
    /// ([PasskeyOffer]).
    ///
    /// @param lifetime  how long the login lasts
    /// @param devices   names the device from the request
    /// @param targetUrl where to go once logged in
    /// @return the handler for the button on the link page
    static LoginSuccessHandler byLink(Duration lifetime, DeviceNames devices, String targetUrl) {
        return new LoginSuccessHandler(lifetime, devices, true, new SimpleUrlAuthenticationSuccessHandler(targetUrl));
    }

    /// A passkey login answers the page's script, which then goes to
    /// `redirectUrl`. The body is what Spring's own passkey script expects, so
    /// a client written against Spring's documentation works here too.
    ///
    /// @param lifetime  how long the login lasts
    /// @param devices   names the device from the request
    /// @param targetUrl where the script goes once logged in
    /// @return the handler for the passkey login filter
    static LoginSuccessHandler byPasskey(Duration lifetime, DeviceNames devices, String targetUrl) {
        byte[] body = JSON.writeValueAsBytes(new PasskeyLoggedIn(true, targetUrl));
        return new LoginSuccessHandler(lifetime, devices, false, (request, response, authentication) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(body);
        });
    }

    /// Development login keeps the regular lifetime and device tracking, and
    /// skips the passkey offer because the developer selected an account.
    ///
    /// @param lifetime how long the selected kind of login lasts
    /// @param devices names the device from the request
    /// @param targetUrl the selected account kind's home page
    /// @param api whether to return JSON instead of redirecting
    /// @return the handler for immediate development login
    static LoginSuccessHandler inDevelopment(Duration lifetime, DeviceNames devices, String targetUrl, boolean api) {
        if (api) {
            return byPasskey(lifetime, devices, targetUrl);
        }
        return new LoginSuccessHandler(lifetime, devices, false,
                new SimpleUrlAuthenticationSuccessHandler(targetUrl));
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession();
        session.setMaxInactiveInterval(Math.toIntExact(lifetime.toSeconds()));
        String device = devices.of(request.getHeader(HttpHeaders.USER_AGENT));
        LoginSession.start(session, Instant.now().plus(lifetime), device);
        request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, Boolean.TRUE);
        if (offerPasskey) {
            PasskeyOffer.make(session);
        }
        then.onAuthenticationSuccess(request, response, authentication);
    }

    /// @param authenticated always true; a failed login gets 401 and no body
    /// @param redirectUrl   where the script goes next
    private record PasskeyLoggedIn(boolean authenticated, String redirectUrl) {
    }
}
