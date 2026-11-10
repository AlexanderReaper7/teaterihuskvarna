package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.administrator.AdministratorAlreadyExists;
import se.teaterihuskvarna.administrator.AdministratorDetails;
import se.teaterihuskvarna.administrator.AdministratorService;
import se.teaterihuskvarna.administrator.CannotRemoveSelf;
import se.teaterihuskvarna.administrator.NewAdministrator;
import se.teaterihuskvarna.administrator.NoSuchAdministrator;
import se.teaterihuskvarna.administrator.TooFewAdministrators;
import se.teaterihuskvarna.content.ContentSettings;
import se.teaterihuskvarna.login.AdministratorId;
import se.teaterihuskvarna.login.PasskeyUrls;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.offer.OfferService;
import se.teaterihuskvarna.volunteer.ShiftService;

/// The administrators' start page. It opens with what needs doing: this year's
/// fees, the upcoming shifts with free places and the upcoming offers. Then it
/// lists who the administrators are, with a form to add one and a link to
/// remove each, and the logged-in administrator's own passkeys and devices.
/// Spring Security lets only an administrator account reach it.
///
/// A failed add shows the page again with the typed values kept. The link to
/// remove leads to a page that asks first. The removal itself has no typed
/// values to keep, so it always redirects back, with its outcome on the
/// [Redirect]. What a redirect carries lives in the session, which Spring
/// Session serialises to PostgreSQL, so it is plain strings.
///
/// The page offers no link to remove oneself, and the service refuses it for a
/// form posted anyway.
@Controller
public class AdministratorPageController {

    private static final String PAGE = "/admin";

    private final AdministratorService administrators;
    private final MemberService members;
    private final OfferService offers;
    private final ShiftService shifts;
    private final ContentSettings content;
    private final Copy copy;
    private final PasskeySection passkeys;
    private final DeviceSection devices;
    private final Templates templates;

    AdministratorPageController(AdministratorService administrators, MemberService members, OfferService offers,
            ShiftService shifts, ContentSettings content, Copy copy, PasskeySection passkeys, DeviceSection devices,
            Templates templates) {
        this.administrators = administrators;
        this.members = members;
        this.offers = offers;
        this.shifts = shifts;
        this.content = content;
        this.copy = copy;
        this.passkeys = passkeys;
        this.devices = devices;
        this.templates = templates;
    }

    /// @param signedIn the logged-in administrator
    /// @param session  holds whether a link login just happened, which offers a passkey
    /// @param request  may say the offer was declined on this browser
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the start page: what needs doing, the administrators, an empty form, passkeys and devices
    @GetMapping(PAGE)
    public View overview(@AuthenticationPrincipal SignedIn signedIn, HttpSession session,
            HttpServletRequest request, CsrfField csrf, Feedback feedback) {
        return page(signedIn, session, new NewAdministrator("", ""), FieldErrors.none(),
                passkeys.offer(session, request), csrf, feedback);
    }

    /// @param signedIn the logged-in administrator, recorded as the one who added
    /// @param form     the submitted address and name
    /// @param session  the asking request's session, which the devices list marks
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return a redirect to the page, or the page again with what was wrong
    @PostMapping(PAGE + "/administratorer")
    public View add(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") NewAdministrator form,
            HttpSession session, CsrfField csrf, Feedback feedback) {
        AdministratorDetails added;
        try {
            added = administrators.add(form, signedIn.administratorId());
        } catch (ConstraintViolationException e) {
            return page(signedIn, session, form, FieldErrors.of(e), false, csrf, feedback);
        } catch (AdministratorAlreadyExists e) {
            return page(signedIn, session, form, FieldErrors.none(), false, csrf,
                    feedback.withError(copy.text("admin.error.alreadyExists")));
        }
        return Redirect.to(PAGE).notice(copy.text("admin.added", added.fullName()));
    }

    /// The confirmation step before a removal. It asks about neither the
    /// logged-in administrator nor one who is gone, as when another administrator
    /// removed them while the page stood open: both go back to the start page
    /// with what the removal itself would have said.
    ///
    /// @param signedIn the logged-in administrator
    /// @param id       the administrator to remove, whom the page names
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the page that asks before removing, or a redirect to the start page
    @GetMapping(PAGE + "/administratorer/{id}/ta-bort")
    public View confirmRemove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable AdministratorId id,
            CsrfField csrf, Feedback feedback) {
        if (id.equals(signedIn.administratorId())) {
            return Redirect.to(PAGE).error(copy.text("admin.error.self"));
        }
        AdministratorDetails administrator;
        try {
            administrator = administrators.details(id);
        } catch (NoSuchAdministrator e) {
            return Redirect.to(PAGE).error(copy.text("admin.error.noSuch"));
        }
        return HtmlPage.of(templates.adminAdministratorRemove(copy, csrf, administrator, feedback));
    }

    /// @param signedIn the logged-in administrator, recorded as the one who removed
    /// @param id       the administrator to remove
    /// @return a redirect to the page, with the outcome
    @PostMapping(PAGE + "/administratorer/{id}/ta-bort")
    public View remove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable AdministratorId id) {
        try {
            administrators.remove(id, signedIn.administratorId());
        } catch (CannotRemoveSelf e) {
            return Redirect.to(PAGE).error(copy.text("admin.error.self"));
        } catch (TooFewAdministrators e) {
            return Redirect.to(PAGE).error(copy.text("admin.error.tooFew"));
        } catch (NoSuchAdministrator e) {
            return Redirect.to(PAGE).error(copy.text("admin.error.noSuch"));
        }
        return Redirect.to(PAGE).notice(copy.text("admin.removed"));
    }

    /// @param signedIn the logged-in administrator, whose passkey it must be
    /// @param id       the passkey to remove
    /// @return a redirect to the page, with the outcome
    @PostMapping(PAGE + "/passkeys/{id}/ta-bort")
    public View removePasskey(@AuthenticationPrincipal SignedIn signedIn, @PathVariable String id) {
        return passkeys.remove(signedIn, id, Redirect.to(PAGE));
    }

    /// "Fråga inte igen" on the offer.
    ///
    /// @param signedIn the logged-in administrator
    /// @param request  whether the connection is HTTPS
    /// @param response receives the cookie that stops the offer on this browser
    /// @return a redirect to the page
    @PostMapping(PAGE + "/passkeys/fraga-inte-igen")
    public View declinePasskey(@AuthenticationPrincipal SignedIn signedIn, HttpServletRequest request,
            HttpServletResponse response) {
        passkeys.decline(signedIn, request, response);
        return Redirect.to(PAGE);
    }

    /// @param signedIn the logged-in administrator, whose device it must be
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
    /// @param signedIn the logged-in administrator
    /// @param session  the asking request's session, which stays logged in
    /// @return a redirect to the page, with the outcome
    @PostMapping(PAGE + "/enheter/andra/logga-ut")
    public View endOtherDevices(@AuthenticationPrincipal SignedIn signedIn, HttpSession session) {
        return devices.endOthers(signedIn, session, Redirect.to(PAGE));
    }

    private View page(SignedIn signedIn, HttpSession session, NewAdministrator form, FieldErrors errors,
            boolean offerPasskey, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminOverview(content.studioLink(), copy, csrf, signedIn, members.feeCount(),
                shifts.needingVolunteers(), offers.upcoming(), administrators.list(), form, errors, feedback,
                passkeys.list(signedIn), PasskeyUrls.of(signedIn.kind()), offerPasskey,
                devices.list(signedIn, session), DeviceUrls.of(signedIn.kind())));
    }
}
