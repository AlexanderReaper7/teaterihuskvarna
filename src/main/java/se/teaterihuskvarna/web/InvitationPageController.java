package se.teaterihuskvarna.web;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.teaterihuskvarna.member.InvitationOutcome;
import se.teaterihuskvarna.member.InvitationService;

/// The page an invitation link opens (R019). Anyone may reach it: the token is
/// the credential.
@Controller
public class InvitationPageController {

    private final InvitationService invitations;

    InvitationPageController(InvitationService invitations) {
        this.invitations = invitations;
    }

    /// A GET does not accept, for the reason the login link page gives: a mail
    /// scanner following the link would otherwise use it up.
    ///
    /// @param token the token from the mailed link
    /// @param model receives the token for the button to post
    /// @return the page with the accept button, or the result page for a missing token
    @GetMapping("/inbjudan")
    public String invitation(@RequestParam(required = false) @Nullable String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("outcome", InvitationOutcome.INVALID);
            return "invitation/result";
        }
        model.addAttribute("token", token);
        return "invitation/confirm";
    }

    /// Creates the account. Nobody is logged in: the new account logs in by
    /// link like any other.
    ///
    /// @param token the token the button posted
    /// @param model receives what happened
    /// @return the result page
    @PostMapping("/inbjudan")
    public String accept(@RequestParam(required = false) @Nullable String token, Model model) {
        model.addAttribute("outcome", invitations.accept(token));
        return "invitation/result";
    }
}
