package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.RedirectView;

/// The answer to a request that changed something: the page to show next,
/// and what to tell the person there.
///
/// `Redirect.to("/medlem/hushall").notice(text)` is what
/// `addFlashAttribute("notice", text)` and `return "redirect:/medlem/hushall"`
/// were. The page it leads to reads the notice from its [Feedback].
public final class Redirect implements SmartView {

    private final String path;
    private final @Nullable String error;
    private final @Nullable String notice;

    private Redirect(String path, @Nullable String error, @Nullable String notice) {
        this.path = path;
        this.error = error;
        this.notice = notice;
    }

    /// @param path the page to show next, as a path on this site
    /// @return a redirect that says nothing
    public static Redirect to(String path) {
        return new Redirect(path, null, null);
    }

    /// @param message what was done, in Swedish
    /// @return this redirect with the notice
    public Redirect notice(String message) {
        return new Redirect(path, error, message);
    }

    /// @param message what went wrong, in Swedish
    /// @return this redirect with the error
    public Redirect error(String message) {
        return new Redirect(path, message, notice);
    }

    @Override
    public boolean isRedirectView() {
        return true;
    }

    /// Answers 302, as a view named `redirect:` did, and adds nothing from
    /// the model to the address.
    @Override
    public void render(@Nullable Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        if (error != null) {
            flash.put(Feedback.ERROR, error);
        }
        if (notice != null) {
            flash.put(Feedback.NOTICE, notice);
        }
        new RedirectView(path, true, true, false).render(Map.of(), request, response);
    }
}
