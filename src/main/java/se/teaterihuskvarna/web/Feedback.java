package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.support.RequestContextUtils;

/// What a page tells the person about what they just did: that the form
/// they sent was too old to accept, an error, or a notice.
///
/// A handler declares one as a parameter and passes it to its page, and the
/// page shows it with `@template.tag.feedback`. The error and the notice
/// come from the request before this one, which answered with a [Redirect]
/// that carried them. `stale` comes from the query:
/// `se.teaterihuskvarna.login.RefusedRequests` adds `gammal` when it sends a
/// form whose session is gone back to its page.
///
/// The names the two travel under are written here and nowhere else.
/// [Redirect] writes them and [#of] reads them, where a handler used to write
/// `addFlashAttribute("notice", ..)` and a template read `@param String
/// notice`, and a name spelled differently on either side showed nothing.
///
/// @param stale  whether the form was refused because its session is gone
/// @param error  what went wrong, or null
/// @param notice what was done, or null
public record Feedback(boolean stale, @Nullable String error, @Nullable String notice) {

    static final String ERROR = "error";
    static final String NOTICE = "notice";

    /// The same name as `STALE` in `se.teaterihuskvarna.login.RefusedRequests`,
    /// which is private to the login package.
    private static final String STALE = "gammal";

    /// @param request the request a page is drawn for
    /// @return what the request before it left for this page
    static Feedback of(HttpServletRequest request) {
        Map<String, ?> flash = RequestContextUtils.getInputFlashMap(request);
        return new Feedback(request.getParameter(STALE) != null, text(flash, ERROR), text(flash, NOTICE));
    }

    /// @param message what went wrong in this request, in Swedish
    /// @return this feedback with that error, for a page the handler draws
    ///         itself and does not redirect to
    public Feedback withError(String message) {
        return new Feedback(stale, message, notice);
    }

    private static @Nullable String text(@Nullable Map<String, ?> flash, String name) {
        return flash != null && flash.get(name) instanceof String text ? text : null;
    }
}
