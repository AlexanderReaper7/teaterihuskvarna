package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    MembershipApplicationPageController(MembershipApplicationService applications, AssociationSettings association) {
        this.applications = applications;
        this.association = association;
    }

    /// @return the fee amounts, which the form states
    @ModelAttribute("fees")
    public AssociationSettings fees() {
        return association;
    }

    /// @param model receives an empty form and no errors
    /// @return the application form
    @GetMapping("/bli-medlem")
    public String form(Model model) {
        model.addAttribute("form", new ApplicationForm("", "", null, null, null, null));
        model.addAttribute("errors", FieldErrors.none());
        return "application/form";
    }

    /// The client address is the one the servlet container reports. Behind the
    /// proxy that is the visitor's address only because `application.yaml` tells
    /// the container to trust the proxy's forwarded header; this class does not read
    /// the header itself.
    ///
    /// @param form    the submitted fields, bound by name
    /// @param request the request, for the client address the rate limit counts
    /// @param model   receives the errors when the form is shown again
    /// @return a redirect to the sent page, or the form again with what was wrong
    @PostMapping("/bli-medlem")
    public String apply(@ModelAttribute("form") ApplicationForm form, HttpServletRequest request, Model model) {
        try {
            applications.apply(form, request.getRemoteAddr());
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            return "application/form";
        }
        return "redirect:/bli-medlem/skickat";
    }

    /// @return the page that says a mail may be on its way
    @GetMapping("/bli-medlem/skickat")
    public String sent() {
        return "application/sent";
    }

    /// Opened from the mailed link. A GET does not confirm, for the reason the login
    /// link page gives: a mail scanner following the link would otherwise use it up.
    ///
    /// @param token the token from the mailed link
    /// @param model receives the token for the button to post
    /// @return the page with the confirm button, or the expired page when there is no token
    @GetMapping("/bli-medlem/bekrafta")
    public String confirmation(@RequestParam(required = false) @Nullable String token, Model model) {
        if (token == null || token.isBlank()) {
            return "application/expired";
        }
        model.addAttribute("token", token);
        return "application/confirm";
    }

    /// @param token the token the confirm button posted
    /// @param model receives the new member's name, address and the bankgiro
    /// @return the welcome page with the payment instruction, or the expired page
    @PostMapping("/bli-medlem/bekrafta")
    public String confirm(@RequestParam String token, Model model) {
        Optional<Welcome> welcome = applications.confirm(token);
        if (welcome.isEmpty()) {
            return "application/expired";
        }
        model.addAttribute("welcome", welcome.get());
        return "application/welcome";
    }
}
