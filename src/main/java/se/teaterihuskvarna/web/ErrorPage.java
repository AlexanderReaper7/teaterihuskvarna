package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/// The page for every error a browser sees, in place of Spring's Whitelabel
/// page: one text for 404 and one for anything else. Boot's error controller
/// asks this class for the view and keeps the status it chose.
///
/// Boot's own resolver would look for views named `error/404` and
/// `error/5xx`, and no view here has a name: a page is a method on
/// `Templates`.
///
/// One request gets no error page. A form refused as stale is sent back to
/// the page it was on, marked so that the page says why. A page a POST drew
/// has an address no GET answers: the mailing's preview at
/// `/admin/utskick/forhandsgranska`, or any form shown again with what was
/// wrong in it, such as `/admin/medlemmar/5/avgift`. The person would be told
/// that something went wrong, for having left a page open too long. They are
/// sent one step up the path instead, with the same mark. In every such case
/// here that is the page that holds the form, and if it draws no page either,
/// the same happens again from there.
@Component
class ErrorPage implements ErrorViewResolver {

    /// What a GET gets from an address that draws no page: a value in the path
    /// that is not a number, no handler at all, or a handler for POST only.
    private static final Set<HttpStatus> NO_PAGE =
            Set.of(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);

    private final Copy copy;
    private final Templates templates;

    ErrorPage(Copy copy, Templates templates) {
        this.copy = copy;
        this.templates = templates;
    }

    /// @param request the request Boot's error controller is answering
    /// @param status  the status the response will have
    /// @param model   Boot's error attributes, which the page does not show
    /// @return the error page, or a redirect to the page above for a stale form that came back to no page
    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        String above = NO_PAGE.contains(status) ? pageAboveAStaleForm(request) : null;
        if (above != null) {
            return new ModelAndView(Feedback.staleAt(above));
        }
        ModelAndView page = new ModelAndView(
                HtmlPage.of(templates.errorPage(copy, status == HttpStatus.NOT_FOUND)));
        page.setStatus(status);
        return page;
    }

    /// The path is the one the browser asked for, shortened, so the redirect
    /// stays on this site. A path with `//` or a backslash in it is left
    /// alone, since a browser reads a `Location` that starts that way as
    /// another host. The start page is not a page above anything: an address
    /// directly under it gets the error page.
    ///
    /// @return the path one step up from the one asked for, or null if the request is not a GET marked as a
    ///         stale form's, or has no page above it
    private static @Nullable String pageAboveAStaleForm(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) || !Feedback.of(request).stale()) {
            return null;
        }
        if (!(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String asked)
                || !asked.startsWith("/") || asked.contains("//") || asked.contains("\\")) {
            return null;
        }
        String path = asked.endsWith("/") ? asked.substring(0, asked.length() - 1) : asked;
        int step = path.lastIndexOf('/');
        return step > 0 ? path.substring(0, step) : null;
    }
}
