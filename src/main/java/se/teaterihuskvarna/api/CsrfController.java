package se.teaterihuskvarna.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// Hands an API client the CSRF token it must send with every POST and DELETE.
///
/// The REST adapter authenticates with the same session cookie as the pages, so
/// it needs the same CSRF protection. A page gets its token in a hidden form
/// field; an API client asks here first and sends the token back in the header
/// this names.
@RestController
public class CsrfController {

    /// Reading the token stores it in the session, which is the point: the client
    /// gets a session cookie and a token that belongs to it.
    ///
    /// @param token the request's token, which Spring Security resolves
    /// @return the header and parameter names, and the token
    @GetMapping("/api/csrf")
    public Csrf csrf(CsrfToken token) {
        return new Csrf(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    /// A copy of the token's fields. Spring Security's own `CsrfToken` is a deferred
    /// wrapper, not something to hand a JSON serialiser.
    ///
    /// @param headerName    the header to send the token in
    /// @param parameterName the form parameter to send it in instead
    /// @param token         the token
    public record Csrf(String headerName, String parameterName, String token) {
    }
}
