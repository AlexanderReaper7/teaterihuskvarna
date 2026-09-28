package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.mailing.BrevoUnavailable;
import se.teaterihuskvarna.mailing.EmptyMailing;
import se.teaterihuskvarna.mailing.MailingDetails;
import se.teaterihuskvarna.mailing.MailingForm;
import se.teaterihuskvarna.mailing.MailingService;
import se.teaterihuskvarna.mailing.NoRecipients;
import se.teaterihuskvarna.mailing.NoSuchMailing;
import se.teaterihuskvarna.mailing.UnknownAudience;
import se.teaterihuskvarna.mailing.UnknownContent;

/// Mailings (R022 to R025): the form that picks an audience, words and
/// content, the preview of the mail, the draft it creates in Brevo, a test
/// send to the administrator, and the log with Brevo's numbers. Spring
/// Security lets only an administrator account reach it.
///
/// The preview is a page of its own that carries the form in hidden fields,
/// so going back to change something or going on to create the draft needs
/// nothing stored between the requests.
@Controller
public class AdministratorMailingPageController {

    private static final String LIST = "/admin/utskick";

    private final MailingService mailings;
    private final Copy copy;

    AdministratorMailingPageController(MailingService mailings, Copy copy) {
        this.mailings = mailings;
        this.copy = copy;
    }

    /// @param model receives the form, its choices and the log
    /// @return the mailing page
    @GetMapping(LIST)
    public String list(Model model) {
        return page(new MailingForm("", "", "", null, null), FieldErrors.none(), model);
    }

    /// Shows the form again with what was typed, from the preview's change button.
    ///
    /// @param form  what was typed
    /// @param model receives the form, its choices and the log
    /// @return the mailing page
    @PostMapping(LIST + "/andra")
    public String change(@ModelAttribute("form") MailingForm form, Model model) {
        return page(form, FieldErrors.none(), model);
    }

    /// R023.
    ///
    /// @param form  what was typed
    /// @param model receives the mail's HTML and the form, or the form again with what was wrong
    /// @return the preview, or the mailing page
    @PostMapping(LIST + "/forhandsgranska")
    public String preview(@ModelAttribute("form") MailingForm form, Model model) {
        String html;
        try {
            html = mailings.preview(form);
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), model);
        } catch (EmptyMailing | UnknownContent e) {
            model.addAttribute("error", message(e));
            return page(form, FieldErrors.none(), model);
        }
        model.addAttribute("html", html);
        model.addAttribute("audienceName", mailings.audiences().stream()
                .filter(choice -> choice.value().equals(form.audience()))
                .map(choice -> choice.name())
                .findFirst()
                .orElse(""));
        return "admin/mailingPreview";
    }

    /// R022: creates the list and the draft in Brevo.
    ///
    /// @param signedIn   the administrator, recorded as the one who prepared it
    /// @param form       what was typed
    /// @param model      receives the form again when something is wrong
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the mailing, or the mailing page with what was wrong
    @PostMapping(LIST)
    public String prepare(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") MailingForm form,
            Model model, RedirectAttributes redirected) {
        MailingDetails prepared;
        try {
            prepared = mailings.prepare(form, signedIn.id());
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), model);
        } catch (EmptyMailing | UnknownContent | UnknownAudience | NoRecipients | BrevoUnavailable e) {
            model.addAttribute("error", message(e));
            return page(form, FieldErrors.none(), model);
        }
        redirected.addFlashAttribute("notice", copy.text("adminMailings.prepared", prepared.recipients()));
        return "redirect:" + LIST + "/" + prepared.id();
    }

    /// @param signedIn the administrator, whose address a test goes to
    /// @param id       the mailing
    /// @param model    receives the mailing with Brevo's latest numbers
    /// @return the mailing's page
    @GetMapping(LIST + "/{id}")
    public String mailing(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id, Model model) {
        model.addAttribute("mailing", mailings.details(id));
        model.addAttribute("testAddress", signedIn.email());
        return "admin/mailing";
    }

    /// R023: sends the draft to the administrator's own address.
    ///
    /// @param signedIn   the administrator
    /// @param id         the mailing
    /// @param redirected receives the outcome shown after the redirect
    /// @return a redirect to the mailing's page
    @PostMapping(LIST + "/{id}/testa")
    public String sendTest(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            RedirectAttributes redirected) {
        try {
            mailings.sendTest(id, signedIn.email());
            redirected.addFlashAttribute("notice", copy.text("adminMailings.testSent", signedIn.email()));
        } catch (BrevoUnavailable e) {
            redirected.addFlashAttribute("error", copy.text("adminMailings.error.brevo"));
        }
        return "redirect:" + LIST + "/" + id;
    }

    /// Sends the error page with 404, as for any path that does not exist.
    ///
    /// @param response the response to send the error on
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(NoSuchMailing.class)
    public void notFound(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private String message(RuntimeException e) {
        String key = switch (e) {
            case EmptyMailing _ -> "adminMailings.error.empty";
            case UnknownContent _ -> "adminMailings.error.content";
            case UnknownAudience _ -> "adminMailings.error.audience";
            case NoRecipients _ -> "adminMailings.error.noRecipients";
            default -> "adminMailings.error.brevo";
        };
        return copy.text(key);
    }

    private String page(MailingForm form, FieldErrors errors, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("audiences", mailings.audiences());
        model.addAttribute("content", mailings.content());
        model.addAttribute("log", mailings.log());
        return "admin/mailings";
    }
}
