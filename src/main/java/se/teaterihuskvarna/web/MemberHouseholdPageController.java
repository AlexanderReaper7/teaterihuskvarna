package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.AlreadyInHousehold;
import se.teaterihuskvarna.member.ContactForm;
import se.teaterihuskvarna.member.HouseholdDetails;
import se.teaterihuskvarna.member.HouseholdMember;
import se.teaterihuskvarna.member.HouseholdOwnerRequired;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.HouseholdSuccessorRequired;
import se.teaterihuskvarna.member.MemberId;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NewHousehold;
import se.teaterihuskvarna.member.NoSuchHousehold;
import se.teaterihuskvarna.member.NoSuchMember;

/// A member creates and manages their own household, including leaving it.
@Controller
public class MemberHouseholdPageController {

    private static final String HOUSEHOLD = "/medlem/hushall";
    private static final String MEMBERS = "/medlem/hushall/medlemmar";

    private final HouseholdService households;
    private final MemberService members;
    private final Copy copy;
    private final Templates templates;

    MemberHouseholdPageController(HouseholdService households, MemberService members, Copy copy,
            Templates templates) {
        this.households = households;
        this.members = members;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param signedIn the logged-in account
    /// @param csrf the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return household creation or management
    @GetMapping(HOUSEHOLD)
    public View household(@AuthenticationPrincipal SignedIn signedIn, CsrfField csrf, Feedback feedback) {
        HouseholdDetails household = households.forAccount(signedIn.accountId()).orElse(null);
        NewHousehold form = new NewHousehold(household == null ? "" : household.name());
        return householdPage(signedIn, csrf, feedback, form, FieldErrors.none());
    }

    /// @param signedIn the logged-in account
    /// @param form the household name
    /// @param csrf the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return household management or the invalid form
    @PostMapping(HOUSEHOLD)
    public View create(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") NewHousehold form,
            CsrfField csrf, Feedback feedback) {
        try {
            households.createForAccount(signedIn.accountId(), form);
        } catch (ConstraintViolationException e) {
            return householdPage(signedIn, csrf, feedback, form, FieldErrors.of(e));
        } catch (AlreadyInHousehold e) {
            return Redirect.to(HOUSEHOLD).error(copy.text("member.household.alreadyExists"));
        }
        return Redirect.to(HOUSEHOLD).notice(copy.text("household.created", form.name().strip()));
    }

    /// @param signedIn the logged-in account
    /// @param form the household's new name
    /// @param csrf the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return household management or the invalid form
    @PostMapping(HOUSEHOLD + "/namn")
    public View rename(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") NewHousehold form,
            CsrfField csrf, Feedback feedback) {
        try {
            households.renameForAccount(signedIn.accountId(), form);
        } catch (ConstraintViolationException e) {
            return householdPage(signedIn, csrf, feedback, form, FieldErrors.of(e));
        }
        return Redirect.to(HOUSEHOLD).notice(copy.text("member.household.saved"));
    }

    /// @param signedIn the logged-in account
    /// @param csrf the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the form for adding a person
    @GetMapping(HOUSEHOLD + "/ny")
    public View newMember(@AuthenticationPrincipal SignedIn signedIn, CsrfField csrf, Feedback feedback) {
        HouseholdDetails household = households.forAccount(signedIn.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        MemberId memberId = members.findByAccount(signedIn.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).id();
        if (!household.ownedBy(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return memberForm(csrf, feedback, new ContactForm("", "", "", "", ""), FieldErrors.none(), MEMBERS);
    }

    /// @param signedIn the logged-in account
    /// @param form the new person's details
    /// @param csrf the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return household management or the invalid form
    @PostMapping(MEMBERS)
    public View addMember(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") ContactForm form,
            CsrfField csrf, Feedback feedback) {
        try {
            households.addForAccount(signedIn.accountId(), form);
        } catch (ConstraintViolationException e) {
            return memberForm(csrf, feedback, form, FieldErrors.of(e), MEMBERS);
        }
        return Redirect.to(HOUSEHOLD).notice(copy.text("member.household.memberAdded", form.fullName().strip()));
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to edit
    /// @param csrf the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the contact form
    @GetMapping(MEMBERS + "/{memberId}")
    public View editMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable MemberId memberId,
            CsrfField csrf, Feedback feedback) {
        return memberForm(csrf, feedback, households.memberForAccount(signedIn.accountId(), memberId),
                FieldErrors.none(), MEMBERS + "/" + memberId);
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to edit
    /// @param form the new contact details
    /// @param csrf the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return household management or the invalid form
    @PostMapping(MEMBERS + "/{memberId}")
    public View updateMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable MemberId memberId,
            @ModelAttribute("form") ContactForm form, CsrfField csrf, Feedback feedback) {
        try {
            households.updateForAccount(signedIn.accountId(), memberId, form);
        } catch (ConstraintViolationException e) {
            return memberForm(csrf, feedback, form, FieldErrors.of(e), MEMBERS + "/" + memberId);
        }
        return Redirect.to(HOUSEHOLD).notice(copy.text("member.household.memberSaved"));
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to remove, possibly the caller
    /// @param csrf the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the confirmation page
    @GetMapping(MEMBERS + "/{memberId}/ta-bort")
    public View removeForm(@AuthenticationPrincipal SignedIn signedIn, @PathVariable MemberId memberId,
            CsrfField csrf, Feedback feedback) {
        String fullName = households.memberForAccount(signedIn.accountId(), memberId).fullName();
        HouseholdDetails household = households.forAccount(signedIn.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<HouseholdMember> successors = household.ownedBy(memberId) ? household.members().stream()
                .filter(member -> member.hasAccount() && !member.id().equals(memberId)).toList() : List.of();
        return HtmlPage.of(templates.memberHouseholdRemove(copy, csrf, fullName, memberId, successors, feedback,
                leaving(signedIn, memberId)));
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to remove, possibly the caller
    /// @param successorMemberId the next owner if the caller leaves, or null
    /// @param csrf the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the member overview, including when the caller has just left
    @PostMapping(MEMBERS + "/{memberId}/ta-bort")
    public View remove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable MemberId memberId,
            @RequestParam(required = false) @Nullable MemberId successorMemberId, CsrfField csrf,
            Feedback feedback) {
        boolean leaving = leaving(signedIn, memberId);
        try {
            households.removeForAccount(signedIn.accountId(), memberId, successorMemberId);
        } catch (HouseholdSuccessorRequired e) {
            return removeForm(signedIn, memberId, csrf,
                    feedback.withError(copy.text("household.successor.required")));
        }
        return Redirect.to("/medlem")
                .notice(copy.text(leaving ? "member.household.left" : "member.household.memberRemoved"));
    }

    /// @param response receives 404 for a missing household or a member outside it
    /// @throws IOException if the response cannot be written
    @ExceptionHandler({NoSuchMember.class, NoSuchHousehold.class})
    public void missing(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    /// @param response receives 403 for household edits by another member
    /// @throws IOException if the response cannot be written
    @ExceptionHandler(HouseholdOwnerRequired.class)
    public void forbidden(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.FORBIDDEN.value());
    }

    /// Whether the person to remove is the caller, who then leaves the household
    /// and is told so in the first person.
    private boolean leaving(SignedIn signedIn, MemberId memberId) {
        return members.findByAccount(signedIn.accountId()).map(own -> own.id().equals(memberId)).orElse(false);
    }

    private View householdPage(SignedIn signedIn, CsrfField csrf, Feedback feedback, NewHousehold form,
            FieldErrors errors) {
        MemberId ownMemberId = members.findByAccount(signedIn.accountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).id();
        return HtmlPage.of(templates.memberHousehold(copy, csrf,
                households.forAccount(signedIn.accountId()).orElse(null), ownMemberId, form, errors, feedback));
    }

    private View memberForm(CsrfField csrf, Feedback feedback, ContactForm form, FieldErrors errors,
            String action) {
        return HtmlPage.of(templates.memberHouseholdMember(copy, csrf, form, errors, action, feedback));
    }
}
