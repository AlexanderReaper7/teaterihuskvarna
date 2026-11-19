package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

/// The hidden field every form on the site carries, so Spring Security accepts its POST.
///
/// Built from the token before the template renders, not inside it. Spring
/// Security defers the token, and the first read is what stores it in the session.
/// A read from the template could come after the response has started going out,
/// once the page passes the output buffer, and a session created then never gets
/// its cookie to the browser: the form would carry a token for a session the
/// browser does not have, and its POST would fail with 403.
///
/// @param name  the request parameter Spring Security reads, `_csrf` by default
/// @param value the token
public record CsrfField(String name, String value) {

    /// Reads the token, which stores it in the session and so creates one
    /// for a visitor who had none. A handler gets its field as a parameter,
    /// through [PageArguments], and only a handler that shows a form asks for
    /// one, so the start page writes no session row for a visitor who never
    /// fills in a form. That is what Spring Security's deferred token exists
    /// for.
    ///
    /// @param request a request that came through Spring Security's filters
    /// @return the field
    /// @throws IllegalStateException if the request carries no token
    static CsrfField of(HttpServletRequest request) {
        if (!(request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token)) {
            throw new IllegalStateException("No CSRF token on the request for " + request.getRequestURI());
        }
        return new CsrfField(token.getParameterName(), token.getToken());
    }
}
