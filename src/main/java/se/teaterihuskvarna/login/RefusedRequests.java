package se.teaterihuskvarna.login;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/// What a refused request gets in place of Spring's Whitelabel 403 page. Under
/// `/api/` it is still a bare 403.
///
/// A path no rule mentions answers 404, as [UnknownPaths] explains.
///
/// A form whose CSRF token matches no session goes back to the page it came
/// from, with `gammal` added to the query so the page can ask the person to send
/// it again. That happens when the browser restarted, or the session timed out,
/// while the page stayed open. The page is the `Referer`, taken only when it
/// names this host and a path on it; without one the chain's login page stands
/// in. `se.teaterihuskvarna.web.PageModel` reads the same parameter name.
///
/// Any other refusal is a logged-in person asking for a page their login does
/// not reach, such as a member opening `/admin`. They go to the chain's login
/// page, where an anonymous visitor goes too.
final class RefusedRequests implements AccessDeniedHandler {

    /// The REST adapter's paths, which get status codes rather than pages.
    static final RequestMatcher API = PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    private static final String STALE = "gammal";

    private final LoginUrls urls;
    private final AccessDeniedHandler bare = new AccessDeniedHandlerImpl();

    /// @param urls the chain's login paths
    RefusedRequests(LoginUrls urls) {
        this.urls = urls;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException denied)
            throws IOException, ServletException {
        if (response.isCommitted()) {
            bare.handle(request, response, denied);
            return;
        }
        if (UnknownPaths.MARKED.matches(request)) {
            UnknownPaths.notFound(request, response);
            return;
        }
        if (API.matches(request)) {
            bare.handle(request, response, denied);
            return;
        }
        String target = denied instanceof CsrfException ? back(request) : urls.page();
        response.sendRedirect(target);
    }

    private String back(HttpServletRequest request) {
        UriComponents page = sameHostPage(request);
        UriComponentsBuilder target = page == null
                ? UriComponentsBuilder.fromPath(urls.page())
                : UriComponentsBuilder.fromPath(page.getPath()).query(page.getQuery());
        return target.replaceQueryParam(STALE).queryParam(STALE).build().toUriString();
    }

    /// The `Referer`, when it is a path on the host this request came to. A path
    /// starting `//` is refused too, since a browser reads it as another host.
    /// Spring 7's parser already folds `//` in a path to one slash, so the check
    /// only matters if that changes.
    private static @Nullable UriComponents sameHostPage(HttpServletRequest request) {
        String referer = request.getHeader(HttpHeaders.REFERER);
        if (referer == null) {
            return null;
        }
        UriComponents page;
        try {
            page = UriComponentsBuilder.fromUriString(referer).build();
        } catch (IllegalArgumentException e) {
            return null;
        }
        String path = page.getPath();
        boolean sameHost = request.getServerName().equalsIgnoreCase(page.getHost());
        if (!sameHost || path == null || !path.startsWith("/") || path.startsWith("//")) {
            return null;
        }
        return page;
    }
}
