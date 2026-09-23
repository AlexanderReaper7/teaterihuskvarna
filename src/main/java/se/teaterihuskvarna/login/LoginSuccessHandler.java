package se.teaterihuskvarna.login;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import tools.jackson.databind.json.JsonMapper;

/// Gives a new login its lifetime, then sends the browser on. One instance per
/// [LoginKind] and way of logging in, built by [SecurityConfiguration] and
/// [PasskeyLogin].
///
/// Two settings together make a login last its configured time and no longer.
/// The session's max inactive interval, stored with the session in PostgreSQL,
/// is the one that ends the login: 30 days without a request for a member, 8
/// hours for an administrator. The request attribute tells Spring Session's
/// `DefaultCookieSerializer` to give the cookie a max age, so it survives the
/// browser closing; without it the cookie is a session cookie and the login
/// ends with the browser, whatever the server allows. The long max age does
/// not extend anything, since a cookie for an expired session finds nothing.
///
/// Spring Boot sets the serializer's remember-me attribute to
/// `SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR` whenever Spring
/// Security is on the classpath, and [SecurityConfiguration] sets it again so
/// this class does not depend on that.
final class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final int lifetimeSeconds;
    private final boolean offerPasskey;
    private final AuthenticationSuccessHandler then;

    private LoginSuccessHandler(Duration lifetime, boolean offerPasskey, AuthenticationSuccessHandler then) {
        this.lifetimeSeconds = Math.toIntExact(lifetime.toSeconds());
        this.offerPasskey = offerPasskey;
        this.then = then;
    }

    /// A link login redirects, and the page it lands on offers a passkey
    /// ([PasskeyOffer]).
    ///
    /// @param lifetime  how long the login lasts without a request
    /// @param targetUrl where to go once logged in
    /// @return the handler for the button on the link page
    static LoginSuccessHandler byLink(Duration lifetime, String targetUrl) {
        return new LoginSuccessHandler(lifetime, true, new SimpleUrlAuthenticationSuccessHandler(targetUrl));
    }

    /// A passkey login answers the page's script, which then goes to
    /// `redirectUrl`. The body is what Spring's own passkey script expects, so
    /// a client written against Spring's documentation works here too.
    ///
    /// @param lifetime  how long the login lasts without a request
    /// @param targetUrl where the script goes once logged in
    /// @return the handler for the passkey login filter
    static LoginSuccessHandler byPasskey(Duration lifetime, String targetUrl) {
        byte[] body = JSON.writeValueAsBytes(new PasskeyLoggedIn(true, targetUrl));
        return new LoginSuccessHandler(lifetime, false, (request, response, authentication) -> {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(body);
        });
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        request.getSession().setMaxInactiveInterval(lifetimeSeconds);
        request.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, Boolean.TRUE);
        if (offerPasskey) {
            PasskeyOffer.make(request.getSession());
        }
        then.onAuthenticationSuccess(request, response, authentication);
    }

    /// @param authenticated always true; a failed login gets 401 and no body
    /// @param redirectUrl   where the script goes next
    private record PasskeyLoggedIn(boolean authenticated, String redirectUrl) {
    }
}
