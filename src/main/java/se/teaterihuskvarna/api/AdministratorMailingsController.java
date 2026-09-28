package se.teaterihuskvarna.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.mailing.AudienceChoice;
import se.teaterihuskvarna.mailing.MailingContent;
import se.teaterihuskvarna.mailing.MailingDetails;
import se.teaterihuskvarna.mailing.MailingForm;
import se.teaterihuskvarna.mailing.MailingService;

/// Mailings (R022 to R025), as `/admin/utskick` does them.
@RestController
public class AdministratorMailingsController {

    private final MailingService mailings;

    AdministratorMailingsController(MailingService mailings) {
        this.mailings = mailings;
    }

    /// @return the audiences a mailing can go to
    @GetMapping("/api/admin/mailings/audiences")
    public List<AudienceChoice> audiences() {
        return mailings.audiences();
    }

    /// @return the published events and news a mailing can include
    @GetMapping("/api/admin/mailings/content")
    public MailingContent content() {
        return mailings.content();
    }

    /// R023.
    ///
    /// @param form the subject, words, audience and content
    /// @return the mail as HTML, as Brevo will get it
    @PostMapping(value = "/api/admin/mailings/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@RequestBody MailingForm form) {
        return mailings.preview(form);
    }

    /// R022: creates the list and the draft in Brevo.
    ///
    /// @param signedIn the administrator, recorded as the one who prepared it
    /// @param form     the subject, words, audience and content
    /// @return the logged mailing
    @PostMapping("/api/admin/mailings")
    @ResponseStatus(HttpStatus.CREATED)
    public MailingDetails prepare(@AuthenticationPrincipal SignedIn signedIn, @RequestBody MailingForm form) {
        return mailings.prepare(form, signedIn.id());
    }

    /// R025.
    ///
    /// @return every mailing with Brevo's numbers, newest first
    @GetMapping("/api/admin/mailings")
    public List<MailingDetails> log() {
        return mailings.log();
    }

    /// @param id the mailing
    /// @return the mailing with Brevo's numbers
    @GetMapping("/api/admin/mailings/{id}")
    public MailingDetails details(@PathVariable long id) {
        return mailings.details(id);
    }

    /// R023: sends the draft to the signed-in administrator's own address.
    ///
    /// @param signedIn the administrator
    /// @param id       the mailing
    @PostMapping("/api/admin/mailings/{id}/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendTest(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id) {
        mailings.sendTest(id, signedIn.email());
    }
}
