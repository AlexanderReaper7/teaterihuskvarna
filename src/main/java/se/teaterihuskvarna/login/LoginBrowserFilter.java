package se.teaterihuskvarna.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/// Sets the [LoginBrowser] cookie on every request for a link, before
/// [LinkRequestLimitFilter] and Spring's token filter see it. Every request gets
/// it, whether the address is known, unknown or over the limit, so the response
/// does not tell them apart. In the member chain a membership application gets
/// it too, in every case as well, so that confirming the application logs the
/// new member in only in the browser that sent it.
///
/// One instance per filter chain, built by [SecurityConfiguration] and not a
/// bean, for the reason [LinkRequestLimitFilter] gives.
final class LoginBrowserFilter extends OncePerRequestFilter {

    private final LoginUrls urls;
    private final Duration lifetime;
    private final RequestMatcher generate;

    /// @param urls         the paths of the login this filter serves
    /// @param lifetime     how long a link works
    /// @param applications the paths a membership application is posted to, which also get the cookie
    LoginBrowserFilter(LoginUrls urls, Duration lifetime, List<String> applications) {
        this.urls = urls;
        this.lifetime = lifetime;
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> matchers = new ArrayList<>();
        matchers.add(paths.matcher(HttpMethod.POST, urls.page()));
        applications.forEach(path -> matchers.add(paths.matcher(HttpMethod.POST, path)));
        this.generate = new OrRequestMatcher(matchers);
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
