package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
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

    /// @param token the request's token, or null where Spring Security put none
    /// @return the field, or null when there is no token
    static @Nullable CsrfField of(@Nullable CsrfToken token) {
        if (token == null) {
            return null;
        }
        return new CsrfField(token.getParameterName(), token.getToken());
    }
}
