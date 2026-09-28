package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.content.PreviewPass;
import se.teaterihuskvarna.content.Previews;

/// Where the Studio's Presentation tool opens the site, and where the preview
/// ends: R009. `studio/sanity.config.ts` names both paths.
///
/// The cookie is `SameSite=None; Partitioned`, because the Studio shows the
/// site in a frame from another site, where a browser sends only such a
/// cookie. `Partitioned` keeps it to that frame: the same browser visiting the
/// site directly does not send it, so the preview never leaks into ordinary
/// browsing. `SameSite=None` requires `Secure`, so the preview needs HTTPS, as
/// the Studio does anyway.
@Controller
public class PreviewController {

    private static final Pattern LOCAL_PATH = Pattern.compile("/(?!/)[A-Za-z0-9._~%/?=&-]*");

    private final Previews previews;

    PreviewController(Previews previews) {
        this.previews = previews;
    }

    /// @param secret   the secret the Studio wrote to the dataset
    /// @param pathname the page the Studio wants to show
    /// @param response receives the cookie
    /// @return a redirect to the page, or to the start page if the secret is wrong
    @GetMapping("/forhandsgranska/start")
    public String start(@RequestParam(name = "sanity-preview-secret", required = false) @Nullable String secret,
            @RequestParam(name = "sanity-preview-pathname", required = false) @Nullable String pathname,
            HttpServletResponse response) {
        PreviewPass pass = secret == null ? null : previews.start(secret).orElse(null);
        if (pass == null) {
            return "redirect:/";
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(pass.value(), Previews.LIFETIME.toSeconds()));
        return "redirect:" + localPath(pathname);
    }

    /// @param response receives the expired cookie
    /// @return a redirect to the start page
    @GetMapping("/forhandsgranska/avsluta")
    public String end(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0));
        return "redirect:/";
    }

    private static String cookie(String value, long maxAge) {
        return ResponseCookie.from(Previews.COOKIE, value)
                .path("/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .partitioned(true)
                .build()
                .toString();
    }

    /// Only a path on this site, so the Studio's address cannot send a visitor
    /// elsewhere: `//host` and `/\host` are other sites to a browser, and the
    /// character set leaves no room for either.
    private static String localPath(@Nullable String pathname) {
        return pathname != null && LOCAL_PATH.matcher(pathname).matches() ? pathname : "/";
    }
}
