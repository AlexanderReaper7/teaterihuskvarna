package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.session.Session;

/// The two facts a login keeps in its session beside Spring Security's own:
/// when it ends, and which device it is on. Both are session attributes, so
/// they live and die with the session row and need no table of their own.
///
/// A login lasts a fixed time from the moment it starts, 30 days for a member
/// and 8 hours for an administrator, however often it is used. Spring
/// Session's own expiry slides with every request, so [LoginExpiryFilter]
/// checks the end stored here instead. The page may push the end forward
/// shortly before it comes ([DeviceService#extend]).
///
/// The device is a name such as "Firefox på Windows", read from the
/// `User-Agent` at login ([DeviceNames]). The header itself is not kept.
final class LoginSession {

    static final String ENDS_AT = LoginSession.class.getName() + ".endsAt";
    static final String DEVICE = LoginSession.class.getName() + ".device";

    private LoginSession() {
    }

    /// @param session the session the login just stored itself in
    /// @param endsAt  when the login ends
    /// @param device  the device's name
    static void start(HttpSession session, Instant endsAt, String device) {
        session.setAttribute(ENDS_AT, endsAt);
        session.setAttribute(DEVICE, device);
    }

    /// @param session a logged-in session
    /// @param endsAt  the new end
    static void extend(HttpSession session, Instant endsAt) {
        session.setAttribute(ENDS_AT, endsAt);
    }

    /// @param session the request's session
    /// @return when its login ends, or null if no login set it
    static @Nullable Instant endsAt(HttpSession session) {
        return session.getAttribute(ENDS_AT) instanceof Instant endsAt ? endsAt : null;
    }

    /// @param session a session as Spring Session's repository returns it
    /// @return when its login ends, or null if no login set it
    static @Nullable Instant endsAt(Session session) {
        Object value = session.getAttribute(ENDS_AT);
        return value instanceof Instant endsAt ? endsAt : null;
    }

    /// @param session a session as Spring Session's repository returns it
    /// @return the name of the device it is on, or null if no login set it
    static @Nullable String device(Session session) {
        Object value = session.getAttribute(DEVICE);
        return value instanceof String device ? device : null;
    }
}
