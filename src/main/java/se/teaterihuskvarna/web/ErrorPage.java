package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
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
/// Boot's own resolver would look for views named `error/404` and
/// `error/5xx`, and no view here has a name: a page is a method on
/// `Templates`.
@Component
class ErrorPage implements ErrorViewResolver {

    private final Copy copy;
    private final Templates templates;

    ErrorPage(Copy copy, Templates templates) {
        this.copy = copy;
        this.templates = templates;
    }

    /// @param request the request Boot's error controller is answering
    /// @param status  the status the response will have
    /// @param model   Boot's error attributes, which the page does not show
    /// @return the error page
    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        ModelAndView page = new ModelAndView(
                HtmlPage.of(templates.errorPage(copy, status == HttpStatus.NOT_FOUND)));
        page.setStatus(status);
        return page;
    }
}
