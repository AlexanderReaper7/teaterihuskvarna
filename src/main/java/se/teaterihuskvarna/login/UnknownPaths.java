package se.teaterihuskvarna.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcher;

/// A request no access rule mentions, such as `/finns-inte`, answers 404 for
/// everyone. The member chain's last rule still denies it, as
/// `docs/projektplan.md` requires, and marks the request as it does. The chain's
/// entry point and [RefusedRequests] read the mark and answer 404 in place of a
/// login page or a 403.
///
/// Marking the request, rather than listing the paths the rules mention a
/// second time, keeps the rules the only list. A path added to them stops
/// answering 404 without anything else to change.
final class UnknownPaths {

    private static final String MARK = UnknownPaths.class.getName();

    /// Whether the last rule denied this request.
    static final RequestMatcher MARKED = request -> request.getAttribute(MARK) != null;

    private UnknownPaths() {
    }

    /// @return the member chain's last rule: deny, and mark the request
    static AuthorizationManager<RequestAuthorizationContext> denied() {
        return (authentication, context) -> {
            context.getRequest().setAttribute(MARK, Boolean.TRUE);
            return new AuthorizationDecision(false);
        };
    }

    /// The error page under the site, or a 404 with no body under `/api/`, as
    /// the 401 there has none.
    ///
    /// @param request  the refused request
    /// @param response where the 404 goes
    /// @throws IOException if the error page cannot be sent
    static void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (RefusedRequests.API.matches(request)) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } else {
            response.sendError(HttpStatus.NOT_FOUND.value());
        }
    }
}
