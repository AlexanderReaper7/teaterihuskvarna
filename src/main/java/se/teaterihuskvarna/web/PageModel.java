package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.content.ContentSettings;

/// Puts [Copy] in the model of every page in this package, as `copy`, and
/// whether the page was sent back because its form had gone stale, as `stale`.
///
/// Extra model attributes cost nothing in a template. The JTE starter hands the
/// whole Spring model to the template as a map, and the precompiled template reads
/// only the names it declares with `@param`, so an attribute nobody declared is
/// ignored. A declared name with nothing in the model arrives as null, and nothing
/// checks that at build time: the model is the one part of a JTE page the compiler
/// cannot see.
@ControllerAdvice(basePackageClasses = PageModel.class)
public class PageModel {

    private final Copy copy;
    private final ContentSettings content;

    PageModel(Copy copy, ContentSettings content) {
        this.copy = copy;
        this.content = content;
    }

    /// @return the configured content editor, or null when none is connected
    @ModelAttribute("studioUrl")
    public @Nullable String studioUrl() {
        String url = content.studioUrl();
        return url == null || url.isBlank() ? null : url;
    }

    /// @return the Swedish copy, for the template to read by key
    @ModelAttribute("copy")
    public Copy copy() {
        return copy;
    }

    /// `se.teaterihuskvarna.login.RefusedRequests` adds `gammal` to the query
    /// when it sends a form whose session is gone back to its page.
    ///
    /// @param stale the `gammal` parameter, present or not
    /// @return whether to ask the person to send the form again
    @ModelAttribute("stale")
    public boolean stale(@RequestParam(name = "gammal", required = false) @Nullable String stale) {
        return stale != null;
    }
}
