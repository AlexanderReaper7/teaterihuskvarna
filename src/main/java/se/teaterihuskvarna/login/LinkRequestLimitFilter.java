package se.teaterihuskvarna.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/// Puts [LinkRequestLimiter] in front of Spring's `GenerateOneTimeTokenFilter`.
///
/// A request over the limit is answered here with the same redirect a sent link
/// gets, and never reaches the token filter, so no directory lookup and no mail
/// happen. A form sent without an address goes back to the form: there is
/// nothing to look up or to count.
///
/// One instance per filter chain, built by [SecurityConfiguration] and not a
/// bean. Spring Boot registers every `Filter` bean with the servlet container,
/// which would run this outside the security chain as well.
final class LinkRequestLimitFilter extends OncePerRequestFilter {

    private final LinkRequestLimiter limiter;
    private final RequestMatcher generate;
    private final String form;
    private final String sent;
    private final RedirectStrategy redirect = new DefaultRedirectStrategy();

    /// @param limiter the counts
    /// @param urls    the paths of the login this filter guards
    LinkRequestLimitFilter(LinkRequestLimiter limiter, LoginUrls urls) {
        this.limiter = limiter;
        this.generate = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, urls.page());
        this.form = urls.page();
        this.sent = urls.sent();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!generate.matches(request)) {
            chain.doFilter(request, response);
            return;
        }
        String email = request.getParameter("email");
        if (!StringUtils.hasText(email)) {
            redirect.sendRedirect(request, response, form);
            return;
        }
        if (!limiter.tryAcquire(email, request.getRemoteAddr())) {
            redirect.sendRedirect(request, response, sent);
            return;
        }
        chain.doFilter(request, response);
    }
}
