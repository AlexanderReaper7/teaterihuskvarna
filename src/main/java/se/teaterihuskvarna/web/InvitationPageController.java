package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.member.InvitationOutcome;
import se.teaterihuskvarna.member.InvitationService;

/// The page an invitation link opens (R019). Anyone may reach it: the token is
/// the credential.
@Controller
public class InvitationPageController {

    private final InvitationService invitations;
    private final Copy copy;
    private final Templates templates;

    InvitationPageController(InvitationService invitations, Copy copy, Templates templates) {
        this.invitations = invitations;
        this.copy = copy;
        this.templates = templates;
    }

    /// A GET does not accept, for the reason the login link page gives: a mail
    /// scanner following the link would otherwise use it up. It does look, so
    /// a link that is used or expired says so here, and the button is shown
    /// only while pressing it would create the account.
    ///
    /// @param token    the token from the mailed link, for the button to post
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the page with the accept button, or the result page when the link would create nothing
    @GetMapping("/inbjudan")
    public View invitation(@RequestParam(required = false) @Nullable String token, CsrfField csrf,
            Feedback feedback) {
        Optional<InvitationOutcome> refusal = invitations.refusal(token);
        if (token == null || refusal.isPresent()) {
            return HtmlPage.of(templates.invitationResult(copy, refusal.orElse(InvitationOutcome.INVALID)));
        }
        return HtmlPage.of(templates.invitationConfirm(copy, csrf, feedback, token));
    }

    /// Creates the account. Nobody is logged in: the new account logs in by
    /// link like any other.
    ///
    /// @param token the token the button posted
    /// @return the result page, which says what happened
    @PostMapping("/inbjudan")
    public View accept(@RequestParam(required = false) @Nullable String token) {
        return HtmlPage.of(templates.invitationResult(copy, invitations.accept(token)));
    }
}
