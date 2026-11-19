package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.content.ContentSettings;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.mailing.AudienceHasNonMembers;
import se.teaterihuskvarna.mailing.BrevoUnavailable;
import se.teaterihuskvarna.mailing.DraftUnanswered;
import se.teaterihuskvarna.mailing.EarlierAttemptPending;
import se.teaterihuskvarna.mailing.EmptyAudience;
import se.teaterihuskvarna.mailing.EmptyMailing;
import se.teaterihuskvarna.mailing.MailingDetails;
import se.teaterihuskvarna.mailing.MailingForm;
import se.teaterihuskvarna.mailing.MailingReach;
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
    private final ContentSettings content;
    private final Copy copy;
    private final Templates templates;

    AdministratorMailingPageController(MailingService mailings, ContentSettings content, Copy copy,
            Templates templates) {
        this.mailings = mailings;
        this.content = content;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the mailing page: an empty form, its choices and the log
    @GetMapping(LIST)
    public View list(CsrfField csrf, Feedback feedback) {
        return page(new MailingForm("", "", "", null, null), FieldErrors.none(), csrf, feedback);
    }

    /// Shows the form again with what was typed, from the preview's change button.
    ///
    /// @param form     what was typed
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the mailing page
    @PostMapping(LIST + "/andra")
    public View change(@ModelAttribute("form") MailingForm form, CsrfField csrf, Feedback feedback) {
        return page(form, FieldErrors.none(), csrf, feedback);
    }

    /// R023.
    ///
    /// @param form     what was typed
    /// @param session  keeps the mail for [#previewedMail]
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the preview, or the mailing page with what was wrong
    @PostMapping(LIST + "/forhandsgranska")
    public View preview(@ModelAttribute("form") MailingForm form, HttpSession session, CsrfField csrf,
            Feedback feedback) {
        String html;
        try {
            html = mailings.preview(form);
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), csrf, feedback);
        } catch (EmptyMailing | UnknownContent e) {
            return page(form, FieldErrors.none(), csrf, feedback.withError(message(e)));
        }
        String key = UUID.randomUUID().toString();
        session.setAttribute(PREVIEWED_KEY, key);
        session.setAttribute(PREVIEWED_MAIL, html);
        MailingReach reach;
        try {
            reach = mailings.reach(form.audience());
        } catch (UnknownAudience | BrevoUnavailable e) {
            reach = null;
        }
        String audienceName = mailings.audiences().choices().stream()
                .filter(choice -> choice.value().equals(form.audience()))
                .map(choice -> choice.name())
                .findFirst()
                .orElse("");
        return HtmlPage.of(templates.adminMailingPreview(copy, csrf, form, key, audienceName, reach, feedback));
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
    /// @param signedIn the administrator, recorded as the one who prepared it
    /// @param form     what was typed
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return a redirect to the mailing, or the mailing page with what was wrong
    @PostMapping(LIST)
    public View prepare(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") MailingForm form,
            CsrfField csrf, Feedback feedback) {
        MailingDetails prepared;
        try {
            prepared = mailings.prepare(form, signedIn.administratorId());
        } catch (ConstraintViolationException e) {
            return page(form, FieldErrors.of(e), csrf, feedback);
        } catch (EmptyMailing | UnknownContent | UnknownAudience | AudienceHasNonMembers | EmptyAudience
                | EarlierAttemptPending | BrevoUnavailable e) {
            return page(form, FieldErrors.none(), csrf, feedback.withError(message(e)));
        }
        return Redirect.to(LIST + "/" + prepared.id()).notice(copy.text("adminMailings.prepared"));
    }

    /// @param signedIn the administrator, whose address a test goes to
    /// @param id       the mailing
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the mailing's page, with Brevo's latest numbers
    @GetMapping(LIST + "/{id}")
    public View mailing(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id, CsrfField csrf,
            Feedback feedback) {
        return HtmlPage.of(templates.adminMailing(copy, csrf, mailings.details(id), signedIn.email(), feedback));
    }

    /// R023: sends the draft to the administrator's own address.
    ///
    /// @param signedIn the administrator
    /// @param id       the mailing
    /// @return a redirect to the mailing's page, with the outcome
    @PostMapping(LIST + "/{id}/testa")
    public View sendTest(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        Redirect page = Redirect.to(LIST + "/" + id);
        try {
            mailings.sendTest(id, signedIn.email());
        } catch (BrevoUnavailable e) {
            return page.error(message(e));
        }
        return page.notice(copy.text("adminMailings.testSent", signedIn.email()));
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

    private View page(MailingForm form, FieldErrors errors, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminMailings(content.studioLink(), copy, csrf, form, errors,
                mailings.audiences(), mailings.content(), mailings.log(), feedback));
    }
}
