package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/// The page for every error a browser sees, in place of Spring's Whitelabel
/// page: one text for 404 and one for anything else. Boot's error controller
/// asks this class for the view and keeps the status it chose.
///
/// Boot's own resolver would look for `error/404` and `error/5xx` templates,
/// but the JTE starter does not tell it which templates exist, so it never
/// finds one. The view is not named `error` either, which is the bean name of
/// the Whitelabel view. The model carries [Copy] itself because [PageModel]
/// reaches only this package's controllers, and Boot's is not one of them.
@Component
class ErrorPage implements ErrorViewResolver {

    private final Copy copy;

    ErrorPage(Copy copy) {
        this.copy = copy;
    }

    /// @param request the request Boot's error controller is answering
    /// @param status  the status the response will have
    /// @param model   Boot's error attributes, which the page does not show
    /// @return the error page
    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        return new ModelAndView("error/page", Map.of("copy", copy, "notFound", status == HttpStatus.NOT_FOUND),
                status);
    }
}
