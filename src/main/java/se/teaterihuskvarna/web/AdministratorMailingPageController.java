package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import se.teaterihuskvarna.mailing.AudienceHasNonMembers;
import se.teaterihuskvarna.mailing.BrevoUnavailable;
import se.teaterihuskvarna.mailing.DraftUnanswered;
import se.teaterihuskvarna.mailing.EarlierAttemptPending;
import se.teaterihuskvarna.mailing.EmptyAudience;
import se.teaterihuskvarna.mailing.EmptyMailing;
import se.teaterihuskvarna.mailing.MailingDetails;
import se.teaterihuskvarna.mailing.MailingForm;
import se.teaterihuskvarna.mailing.MailingService;
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
///
/// The mail itself is the one thing stored. The preview page shows it in a
/// frame, and a frame fetches its document with a GET, whose address cannot
/// hold the form: the administrator's own words alone can be 5000 characters.
/// So [#preview] keeps the mail it built in the administrator's session under
/// a new key, and [#previewedMail] answers the frame with it. The session
/// holds one mail, the latest, and the key makes an older preview page's frame
/// answer 404 instead of showing a mail its buttons would not create.
@Controller
public class AdministratorMailingPageController {

    private static final String LIST = "/admin/utskick";

    /// The session attribute with the key of the latest preview.
    private static final String PREVIEWED_KEY = "mailingPreview.key";

    /// The session attribute with the mail of the latest preview, as HTML.
    private static final String PREVIEWED_MAIL = "mailingPreview.mail";

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);
    private static final MediaType TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

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
    /// @param form    what was typed
    /// @param model   receives the form and the key of the mail, or the form again with what was wrong
    /// @param session keeps the mail for [#previewedMail]
    /// @return the preview, or the mailing page
    @PostMapping(LIST + "/forhandsgranska")
    public String preview(@ModelAttribute("form") MailingForm form, Model model, HttpSession session) {
        String html;
        try {
            html = mailings.preview(form);
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), model);
        } catch (EmptyMailing | UnknownContent e) {
            model.addAttribute("error", message(e));
            return page(form, FieldErrors.none(), model);
        }
        String key = UUID.randomUUID().toString();
        session.setAttribute(PREVIEWED_KEY, key);
        session.setAttribute(PREVIEWED_MAIL, html);
        model.addAttribute("mailKey", key);
        try {
            model.addAttribute("reach", mailings.reach(form.audience()));
        } catch (UnknownAudience | BrevoUnavailable e) {
            model.addAttribute("reach", null);
        }
        model.addAttribute("audienceName", mailings.audiences().choices().stream()
                .filter(choice -> choice.value().equals(form.audience()))
                .map(choice -> choice.name())
                .findFirst()
                .orElse(""));
        return "admin/mailingPreview";
    }

    /// R023: the mail of the latest preview, for the frame on the preview page.
    /// `SecurityConfiguration` sends it with the headers that keep it from
    /// running script, from reaching the administrator's session and from being
    /// framed by another site.
    ///
    /// @param key     the key [#preview] gave the mail
    /// @param session where [#preview] kept the mail
    /// @return the mail as HTML, or 404 with a sentence saying why, once a newer preview has replaced it
    @GetMapping(LIST + "/forhandsgranska/{key}")
    public ResponseEntity<String> previewedMail(@PathVariable String key, HttpSession session) {
        if (key.equals(session.getAttribute(PREVIEWED_KEY))
                && session.getAttribute(PREVIEWED_MAIL) instanceof String mail) {
            return ResponseEntity.ok().contentType(HTML).body(mail);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(TEXT)
                .body(copy.text("adminMailings.preview.replaced"));
    }

    /// R022: creates the draft in Brevo.
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
            prepared = mailings.prepare(form, signedIn.administratorId());
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), model);
        } catch (EmptyMailing | UnknownContent | UnknownAudience | AudienceHasNonMembers | EmptyAudience
                | EarlierAttemptPending | BrevoUnavailable e) {
            model.addAttribute("error", message(e));
            return page(form, FieldErrors.none(), model);
        }
        redirected.addFlashAttribute("notice", copy.text("adminMailings.prepared"));
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
            redirected.addFlashAttribute("error", message(e));
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
        if (e instanceof AudienceHasNonMembers nonMembers) {
            return copy.text("adminMailings.error.nonMembers", nonMembers.nonMembers());
        }
        String key = switch (e) {
            case EmptyMailing _ -> "adminMailings.error.empty";
            case UnknownContent _ -> "adminMailings.error.content";
            case UnknownAudience _ -> "adminMailings.error.audience";
            case EmptyAudience _ -> "adminMailings.error.noRecipients";
            case EarlierAttemptPending _ -> "adminMailings.error.pending";
            case DraftUnanswered _ -> "adminMailings.error.unanswered";
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
