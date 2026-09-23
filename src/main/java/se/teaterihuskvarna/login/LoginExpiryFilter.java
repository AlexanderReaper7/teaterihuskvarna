package se.teaterihuskvarna.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/// Ends a login whose time is up, counted from when it started rather than
/// from its last request ([LoginSession]). The session is deleted and the
/// chain's access rules then answer as for anyone else: a redirect to the login
/// page, or 401 under `/api/`.
///
/// A logged-in session without an end is treated as over. Every login sets
/// one, so only a session from before absolute lifetimes existed lacks it,
/// and that person logs in again once.
///
/// One instance per filter chain, built by [SecurityConfiguration] and not a
/// bean, for the reason [LinkRequestLimitFilter] gives.
final class LoginExpiryFilter extends OncePerRequestFilter {

    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contexts.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (authentication != null && authentication.getPrincipal() instanceof SignedIn && session != null) {
            Instant endsAt = LoginSession.endsAt(session);
            if (endsAt == null || !Instant.now().isBefore(endsAt)) {
                contexts.clearContext();
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
