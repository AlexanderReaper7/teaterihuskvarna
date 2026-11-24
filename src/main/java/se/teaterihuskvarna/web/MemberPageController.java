package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.login.PasskeyUrls;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.ContactForm;
import se.teaterihuskvarna.member.EmailTaken;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.InvitationRequest;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberHasAccount;
import se.teaterihuskvarna.member.MemberId;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NoSuchMember;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.volunteer.ShiftService;

/// The logged-in member's own page: this year's fee, the offers and volunteer
/// shifts the member has signed up for, contact details, the household with its
/// invitations, passkeys and devices. Spring Security lets only a member's
/// account reach it.
@Controller
public class MemberPageController {

    private static final String PAGE = "/medlem";

    private final MemberService members;
    private final HouseholdService households;
    private final InvitationService invitations;
    private final OfferService offers;
    private final ShiftService shifts;
    private final Copy copy;
    private final PasskeySection passkeys;
    private final DeviceSection devices;
    private final Templates templates;

    MemberPageController(MemberService members, HouseholdService households, InvitationService invitations,
            OfferService offers, ShiftService shifts, Copy copy, PasskeySection passkeys, DeviceSection devices,
            Templates templates) {
        this.members = members;
        this.households = households;
        this.invitations = invitations;
        this.offers = offers;
        this.shifts = shifts;
        this.copy = copy;
        this.passkeys = passkeys;
        this.devices = devices;
        this.templates = templates;
    }

    /// @param signedIn the logged-in account
    /// @param session  holds whether a link login just happened, which offers a passkey
    /// @param request  may say the offer was declined on this browser
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the member page: the member, what they signed up for, their household, passkeys and devices
    @GetMapping(PAGE)
    public View member(@AuthenticationPrincipal SignedIn signedIn, HttpSession session,
            HttpServletRequest request, CsrfField csrf, Feedback feedback) {
        MemberDetails member = member(signedIn);
        return HtmlPage.of(templates.memberOverview(copy, csrf, member,
                households.forAccount(signedIn.accountId()).orElse(null),
                offers.registeredOffers(member.id()), shifts.booked(member.id()),
                passkeys.list(signedIn), PasskeyUrls.of(signedIn.kind()), passkeys.offer(session, request),
                devices.list(signedIn, session), DeviceUrls.of(signedIn.kind()), feedback));
    }

    /// @param signedIn the logged-in account
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the form for the member's own contact details, with their current values
    @GetMapping(PAGE + "/kontaktuppgifter")
    public View contactForm(@AuthenticationPrincipal SignedIn signedIn, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.memberContact(copy, csrf, ContactForm.of(member(signedIn)),
                FieldErrors.none(), feedback));
    }

    /// @param signedIn the logged-in account
    /// @param form     the submitted values
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return a redirect to the member page, or the form again with what was wrong
    @PostMapping(PAGE + "/kontaktuppgifter")
    public View updateContact(@AuthenticationPrincipal SignedIn signedIn,
            @ModelAttribute("form") ContactForm form, CsrfField csrf, Feedback feedback) {
        try {
            members.updateContact(signedIn.accountId(), form);
        } catch (ConstraintViolationException e) {
            return HtmlPage.of(templates.memberContact(copy, csrf, form, FieldErrors.of(e), feedback));
        } catch (NoSuchMember e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, null, e);
        }
        return Redirect.to(PAGE).notice(copy.text("member.contact.saved"));
    }

    /// Invites someone in the member's household who has no account. Sending
    /// again replaces the earlier link.
    ///
    /// @param signedIn the logged-in account, whose household it must be
    /// @param memberId the household member to invite
    /// @param email    the address to send the invitation to
    /// @return a redirect to the member page, with the outcome
    @PostMapping(PAGE + "/hushall/{memberId}/inbjudan")
    public View invite(@AuthenticationPrincipal SignedIn signedIn, @PathVariable MemberId memberId,
            @RequestParam(defaultValue = "") String email) {
        try {
            invitations.inviteToHousehold(signedIn.accountId(), memberId, new InvitationRequest(email));
        } catch (ConstraintViolationException e) {
            return Redirect.to(PAGE).error(firstMessage(e));
        } catch (NoSuchMember e) {
            return Redirect.to(PAGE).error(copy.text("invitation.error.noSuchMember"));
        } catch (MemberHasAccount e) {
            return Redirect.to(PAGE).error(copy.text("invitation.error.hasAccount"));
        } catch (EmailTaken e) {
            return Redirect.to(PAGE).error(copy.text("invitation.error.emailTaken"));
        }
        return Redirect.to(PAGE).notice(copy.text("invitation.sent", email.strip()));
    }

    /// "Fråga inte igen" on the offer.
    ///
    /// @param signedIn the logged-in account
    /// @param request  whether the connection is HTTPS
    /// @param response receives the cookie that stops the offer on this browser
    /// @return a redirect to the page
    @PostMapping(PAGE + "/passkeys/fraga-inte-igen")
    public View declinePasskey(@AuthenticationPrincipal SignedIn signedIn, HttpServletRequest request,
            HttpServletResponse response) {
        passkeys.decline(signedIn, request, response);
        return Redirect.to(PAGE);
    }

    /// @param signedIn the logged-in account
    /// @param id       the passkey to remove
    /// @return a redirect to the page, with the outcome
    @PostMapping(PAGE + "/passkeys/{id}/ta-bort")
    public View removePasskey(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id) {
        return passkeys.remove(signedIn, id, Redirect.to(PAGE));
    }

    /// @param signedIn the logged-in account, whose device it must be
    /// @param id       the device to log out
    /// @param session  the asking request's session
    /// @return a redirect to the page, with the outcome
    @PostMapping(PAGE + "/enheter/{id}/logga-ut")
    public View endDevice(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id,
            HttpSession session) {
        return devices.end(signedIn, id, session, Redirect.to(PAGE));
    }

    /// "Logga ut överallt annars".
    ///
    /// @param signedIn the logged-in account
    /// @param session  the asking request's session, which stays logged in
    /// @return a redirect to the page, with the outcome
    @PostMapping(PAGE + "/enheter/andra/logga-ut")
    public View endOtherDevices(@AuthenticationPrincipal SignedIn signedIn, HttpSession session) {
        return devices.endOthers(signedIn, session, Redirect.to(PAGE));
    }

    /// An account always belongs to a member, so an empty result means the member
    /// was removed while the session lived on. That is a 404, not an empty page.
    private MemberDetails member(SignedIn signedIn) {
        return members.findByAccount(signedIn.accountId())
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
