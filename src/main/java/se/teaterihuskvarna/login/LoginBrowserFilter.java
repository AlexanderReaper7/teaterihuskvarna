package se.teaterihuskvarna.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/// Sets the [LoginBrowser] cookie on every request for a link, before
/// [LinkRequestLimitFilter] and Spring's token filter see it. Every request gets
/// it, whether the address is known, unknown or over the limit, so the response
/// does not tell them apart.
///
/// One instance per filter chain, built by [SecurityConfiguration] and not a
/// bean, for the reason [LinkRequestLimitFilter] gives.
final class LoginBrowserFilter extends OncePerRequestFilter {

    private final LoginUrls urls;
    private final Duration lifetime;
    private final RequestMatcher generate;

    /// @param urls     the paths of the login this filter serves
    /// @param lifetime how long a link works
    LoginBrowserFilter(LoginUrls urls, Duration lifetime) {
        this.urls = urls;
        this.lifetime = lifetime;
        this.generate = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, urls.page());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (generate.matches(request)) {
            LoginBrowser.issue(request, response, urls, lifetime);
        }
        chain.doFilter(request, response);
    }
}
