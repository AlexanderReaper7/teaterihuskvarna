package se.teaterihuskvarna.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/// Asks the directory, on every request, whether the logged-in person can still
/// log in, and treats the request as logged out if not. The session is deleted
/// and the chain's access rules then answer as for anyone else: a redirect to
/// the login page, or 401 under `/api/`.
///
/// Removing an administrator already deletes their sessions ([Sessions#end]),
/// but a login that looked the administrator up just before the removal
/// committed writes its session just after, and that session would work until
/// it expired. So would a request already under way. This check closes both.
/// It costs one indexed lookup per request; the measurement is in
/// `docs/decisions/0015-login-links-on-spring-security.md`.
///
/// One instance per filter chain, built by [SecurityConfiguration] and not a
/// bean, for the reason [LinkRequestLimitFilter] gives.
final class ActiveLoginFilter extends OncePerRequestFilter {

    private final LoginDirectory directory;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();

    /// @param directory the lookup for the chain's kind of login
    ActiveLoginFilter(LoginDirectory directory) {
        this.directory = directory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = contexts.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof SignedIn signedIn
                && signedIn.kind() == directory.kind()
                && directory.findById(signedIn.id()).isEmpty()) {
            contexts.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
