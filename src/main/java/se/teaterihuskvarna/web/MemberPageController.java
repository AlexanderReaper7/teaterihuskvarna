package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.MemberService;

/// The logged-in member's own page, with their passkeys. Spring Security lets
/// only a member's account reach it.
@Controller
public class MemberPageController {

    private static final String REDIRECT = "redirect:/medlem";

    private final MemberService members;
    private final PasskeySection passkeys;

    MemberPageController(MemberService members, PasskeySection passkeys) {
        this.members = members;
        this.passkeys = passkeys;
    }

    /// An account always belongs to a member, so an empty result means the member
    /// was removed while the session lived on. That is a 404, not an empty page.
    ///
    /// @param signedIn the logged-in account
    /// @param session  holds whether a link login just happened, which offers a passkey
    /// @param request  may say the offer was declined on this browser
    /// @param model    receives the member's name, address, household and passkeys
    /// @return the member page
    @GetMapping("/medlem")
    public String member(@AuthenticationPrincipal SignedIn signedIn, HttpSession session,
            HttpServletRequest request, Model model) {
        model.addAttribute("member", members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        passkeys.addTo(model, signedIn, passkeys.offer(session, request));
        return "member/overview";
    }

    /// "Fråga inte igen" on the offer.
    ///
    /// @param signedIn the logged-in account
    /// @param request  whether the connection is HTTPS
    /// @param response receives the cookie that stops the offer on this browser
    /// @return a redirect to the page
    @PostMapping("/medlem/passkeys/fraga-inte-igen")
    public String declinePasskey(@AuthenticationPrincipal SignedIn signedIn, HttpServletRequest request,
            HttpServletResponse response) {
        passkeys.decline(signedIn, request, response);
        return REDIRECT;
    }

    /// @param signedIn   the logged-in account
    /// @param id         the passkey to remove
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the page
    @PostMapping("/medlem/passkeys/{id}/ta-bort")
    public String removePasskey(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id,
            RedirectAttributes redirected) {
        passkeys.remove(signedIn, id, redirected);
        return REDIRECT;
    }
}
