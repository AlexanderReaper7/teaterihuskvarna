package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpSession;

/// Whether the page after a login should offer to add a passkey. A link login
/// sets it; the member and administrator pages take it the first time they
/// render, so the offer shows once per link login and not on every visit.
/// "Inte nu" lasts until the next link login; "Fråga inte igen" is a cookie
/// the page controllers set (`se.teaterihuskvarna.web.PasskeySection`).
/// Logging in with a passkey never offers.
public final class PasskeyOffer {

    private static final String ATTRIBUTE = PasskeyOffer.class.getName();

    private PasskeyOffer() {
    }

    /// @param session the session the login just stored itself in
    static void make(HttpSession session) {
        session.setAttribute(ATTRIBUTE, Boolean.TRUE);
    }

    /// @param session the logged-in session
    /// @return true once after a link login, false after that and after a passkey login
    public static boolean take(HttpSession session) {
        boolean offered = session.getAttribute(ATTRIBUTE) != null;
        if (offered) {
            session.removeAttribute(ATTRIBUTE);
        }
        return offered;
    }
}
