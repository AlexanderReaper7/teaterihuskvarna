package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/// Which menu link leads to the page being shown, for the link's
/// `aria-current`, which a screen reader announces and `site.css` draws.
///
/// The menus are in the layouts, and a layout sees only what the page calling
/// it passes on. Rather than every page passing its path, the layouts ask here,
/// and this reads the path from the request being rendered, which Spring's
/// `DispatcherServlet` keeps in [RequestContextHolder] while a view renders.
public final class Navigation {

    private Navigation() {
    }

    /// @param href a menu link's path, such as `/medlem/erbjudanden`
    /// @return `page` on that path, `true` on a page under it, such as an
    ///         offer's page under `/medlem/erbjudanden`, and null anywhere else,
    ///         which leaves the attribute out
    public static @Nullable String current(String href) {
        String path = path();
        if (href.equals(path)) {
            return "page";
        }
        return path != null && path.startsWith(href + "/") ? "true" : null;
    }

    /// For a menu's first link, such as `/medlem` in the member's menu, which
    /// the menu's other links are all under: only the page itself counts.
    ///
    /// @param href the link's path
    /// @return `page` on that path, and null anywhere else
    public static @Nullable String exactly(String href) {
        return href.equals(path()) ? "page" : null;
    }

    private static @Nullable String path() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRequestURI().substring(request.getContextPath().length());
        }
        return null;
    }
}
