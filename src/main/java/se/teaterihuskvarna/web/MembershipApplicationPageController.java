package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.login.LoginBrowser;
import se.teaterihuskvarna.member.ApplicationForm;
import se.teaterihuskvarna.member.AssociationSettings;
import se.teaterihuskvarna.member.MembershipApplicationService;
import se.teaterihuskvarna.member.Welcome;

/// A visitor's membership application, from the form to the welcome page.
///
/// Validation is the service's, not this controller's: the same constraints run
/// for `POST /api/membership-applications`, and this page only turns the
/// violations back into messages beside the fields.
@Controller
public class MembershipApplicationPageController {

    private final MembershipApplicationService applications;
    private final AssociationSettings association;
    private final PageMetadataFactory metadata;
    private final Copy copy;
    private final Templates templates;

    MembershipApplicationPageController(MembershipApplicationService applications, AssociationSettings association,
            PageMetadataFactory metadata, Copy copy, Templates templates) {
        this.applications = applications;
        this.association = association;
        this.metadata = metadata;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the application form, empty
    @GetMapping("/bli-medlem")
    public View form(CsrfField csrf, Feedback feedback) {
        return formPage(new ApplicationForm("", "", null, null, null, null), FieldErrors.none(), csrf, feedback);
    }

    /// The client address is the one the servlet container reports. Behind the
    /// proxy that is the visitor's address only because `application.yaml` tells
    /// the container to trust the proxy's forwarded header; this class does not read
    /// the header itself. The login cookie is the one the security filters set on
    /// this request, so the welcome page can log the applicant in, in this browser.
    ///
    /// @param form     the submitted fields, bound by name
    /// @param request  the request, for the client address the rate limit counts
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return a redirect to the sent page, or the form again with what was wrong
    @PostMapping("/bli-medlem")
    public View apply(@ModelAttribute("form") ApplicationForm form, HttpServletRequest request, CsrfField csrf,
            Feedback feedback) {
        try {
            applications.apply(form, request.getRemoteAddr(), LoginBrowser.read(request));
        } catch (ConstraintViolationException e) {
            return formPage(form, FieldErrors.of(e), csrf, feedback);
        }
        return Redirect.to("/bli-medlem/skickat");
    }

    /// @return the page that says a mail may be on its way
    @GetMapping("/bli-medlem/skickat")
    public View sent() {
        return HtmlPage.of(templates.applicationSent(copy));
    }

    /// Opened from the mailed link. A GET does not confirm, for the reason the login
    /// link page gives: a mail scanner following the link would otherwise use it up.
    /// It does look, so a link that is used, expired or cut short says so here,
    /// and the button is shown only while pressing it would make a member.
    ///
    /// @param token    the token from the mailed link, for the button to post
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the page with the confirm button, or the expired page when the link would confirm nothing
    @GetMapping("/bli-medlem/bekrafta")
    public View confirmation(@RequestParam(required = false) @Nullable String token, CsrfField csrf,
            Feedback feedback) {
        if (token == null || !applications.confirmable(token)) {
            return HtmlPage.of(templates.applicationExpired(copy));
        }
        return HtmlPage.of(templates.applicationConfirm(copy, csrf, feedback, token));
    }

    /// @param token the token the confirm button posted
    /// @return the welcome page with the new member's name, address, the payment instruction and a login
    ///         that works in the browser that applied, or the expired page
    @PostMapping("/bli-medlem/bekrafta")
    public View confirm(@RequestParam String token) {
        Optional<Welcome> welcome = applications.confirm(token);
        if (welcome.isEmpty()) {
            return HtmlPage.of(templates.applicationExpired(copy));
        }
        return HtmlPage.of(templates.applicationWelcome(copy, welcome.get()));
    }

    /// The form states the fee amounts, and the page has a public search and
    /// sharing description.
    private View formPage(ApplicationForm form, FieldErrors errors, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.applicationForm(copy,
                metadata.fixed("/bli-medlem", "application.heading", "metadata.membership"), csrf, feedback, form,
                errors, association));
    }
}
