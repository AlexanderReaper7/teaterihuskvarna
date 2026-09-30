package se.teaterihuskvarna.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.ContactForm;
import se.teaterihuskvarna.member.EmailTaken;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.InvitationRequest;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberHasAccount;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NoSuchMember;

/// The logged-in member's own page: contact details, this year's fee, the
/// household with its invitations, passkeys and devices. Spring Security lets
/// only a member's account reach it.
@Controller
public class MemberPageController {

    private static final String REDIRECT = "redirect:/medlem";

    private final MemberService members;
    private final HouseholdService households;
    private final InvitationService invitations;
    private final Copy copy;
    private final PasskeySection passkeys;
    private final DeviceSection devices;

    MemberPageController(MemberService members, HouseholdService households, InvitationService invitations,
            Copy copy, PasskeySection passkeys, DeviceSection devices) {
        this.members = members;
        this.households = households;
        this.invitations = invitations;
        this.copy = copy;
        this.passkeys = passkeys;
        this.devices = devices;
    }

    /// @param signedIn the logged-in account
    /// @param session  holds whether a link login just happened, which offers a passkey
    /// @param request  may say the offer was declined on this browser
    /// @param model    receives the member, their household, passkeys and devices
    /// @return the member page
    @GetMapping("/medlem")
    public String member(@AuthenticationPrincipal SignedIn signedIn, HttpSession session,
            HttpServletRequest request, Model model) {
        model.addAttribute("member", member(signedIn));
        model.addAttribute("household", households.forAccount(signedIn.id()).orElse(null));
        passkeys.addTo(model, signedIn, passkeys.offer(session, request));
        devices.addTo(model, signedIn, session);
        return "member/overview";
    }

    /// @param signedIn the logged-in account
    /// @param model    receives the member's current values and no errors
    /// @return the form for the member's own contact details
    @GetMapping("/medlem/kontaktuppgifter")
    public String contactForm(@AuthenticationPrincipal SignedIn signedIn, Model model) {
        model.addAttribute("form", ContactForm.of(member(signedIn)));
        model.addAttribute("errors", FieldErrors.none());
        return "member/contact";
    }

    /// @param signedIn   the logged-in account
    /// @param form       the submitted values
    /// @param model      receives the errors when the form is shown again
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the member page, or the form again with what was wrong
    @PostMapping("/medlem/kontaktuppgifter")
    public String updateContact(@AuthenticationPrincipal SignedIn signedIn,
            @ModelAttribute("form") ContactForm form, Model model, RedirectAttributes redirected) {
        try {
            members.updateContact(signedIn.id(), form);
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            return "member/contact";
        } catch (NoSuchMember e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }
        redirected.addFlashAttribute("notice", copy.text("member.contact.saved"));
        return REDIRECT;
    }

    /// Invites someone in the member's household who has no account. Sending
    /// again replaces the earlier link.
    ///
    /// @param signedIn   the logged-in account, whose household it must be
    /// @param memberId   the household member to invite
    /// @param email      the address to send the invitation to
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the member page
    @PostMapping("/medlem/hushall/{memberId}/inbjudan")
    public String invite(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId,
            @RequestParam(defaultValue = "") String email, RedirectAttributes redirected) {
        try {
            invitations.inviteToHousehold(signedIn.id(), memberId, new InvitationRequest(email));
        } catch (ConstraintViolationException e) {
            redirected.addFlashAttribute("error", firstMessage(e));
            return REDIRECT;
        } catch (NoSuchMember e) {
            redirected.addFlashAttribute("error", copy.text("invitation.error.noSuchMember"));
            return REDIRECT;
        } catch (MemberHasAccount e) {
            redirected.addFlashAttribute("error", copy.text("invitation.error.hasAccount"));
            return REDIRECT;
        } catch (EmailTaken e) {
            redirected.addFlashAttribute("error", copy.text("invitation.error.emailTaken"));
            return REDIRECT;
        }
        redirected.addFlashAttribute("notice", copy.text("invitation.sent", email.strip()));
        return REDIRECT;
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

    /// @param signedIn   the logged-in account, whose device it must be
    /// @param id         the device to log out
    /// @param session    the asking request's session
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the page
    @PostMapping("/medlem/enheter/{id}/logga-ut")
    public String endDevice(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id,
            HttpSession session, RedirectAttributes redirected) {
        devices.end(signedIn, id, session, redirected);
        return REDIRECT;
    }

    /// "Logga ut överallt annars".
    ///
    /// @param signedIn   the logged-in account
    /// @param session    the asking request's session, which stays logged in
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the page
    @PostMapping("/medlem/enheter/andra/logga-ut")
    public String endOtherDevices(@AuthenticationPrincipal SignedIn signedIn, HttpSession session,
            RedirectAttributes redirected) {
        devices.endOthers(signedIn, session, redirected);
        return REDIRECT;
    }

    /// An account always belongs to a member, so an empty result means the member
    /// was removed while the session lived on. That is a 404, not an empty page.
    private MemberDetails member(SignedIn signedIn) {
        return members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /// A form with one field has room for one message, and sorting picks the
    /// same one every time.
    static String firstMessage(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .findFirst()
                .orElse("");
    }
}
