package se.teaterihuskvarna.web;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.AlreadyInHousehold;
import se.teaterihuskvarna.member.ContactForm;
import se.teaterihuskvarna.member.HouseholdDetails;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.NewHousehold;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NoSuchHousehold;
import se.teaterihuskvarna.member.NoSuchMember;

/// A member creates and manages their own household, including leaving it.
@Controller
public class MemberHouseholdPageController {

    private static final String REDIRECT = "redirect:/medlem/hushall";
    private static final String MEMBER_FORM = "member/householdMember";

    private final HouseholdService households;
    private final MemberService members;
    private final Copy copy;

    MemberHouseholdPageController(HouseholdService households, MemberService members, Copy copy) {
        this.households = households;
        this.members = members;
        this.copy = copy;
    }

    /// @param signedIn the logged-in account
    /// @param model receives the household and its name form
    /// @return household creation or management
    @GetMapping("/medlem/hushall")
    public String household(@AuthenticationPrincipal SignedIn signedIn, Model model) {
        HouseholdDetails household = households.forAccount(signedIn.id()).orElse(null);
        model.addAttribute("form", new NewHousehold(household == null ? "" : household.name()));
        model.addAttribute("errors", FieldErrors.none());
        return householdPage(signedIn, model);
    }

    /// @param signedIn the logged-in account
    /// @param form the household name
    /// @param model receives field errors
    /// @param redirected receives the result
    /// @return household management or the invalid form
    @PostMapping("/medlem/hushall")
    public String create(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") NewHousehold form,
            Model model, RedirectAttributes redirected) {
        try {
            households.createForAccount(signedIn.id(), form);
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            return householdPage(signedIn, model);
        } catch (AlreadyInHousehold e) {
            redirected.addFlashAttribute("error", copy.text("member.household.alreadyExists"));
            return REDIRECT;
        }
        redirected.addFlashAttribute("notice", copy.text("household.created", form.name().strip()));
        return REDIRECT;
    }

    /// @param signedIn the logged-in account
    /// @param form the household's new name
    /// @param model receives field errors
    /// @param redirected receives confirmation
    /// @return household management or the invalid form
    @PostMapping("/medlem/hushall/namn")
    public String rename(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") NewHousehold form,
            Model model, RedirectAttributes redirected) {
        try {
            households.renameForAccount(signedIn.id(), form);
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            return householdPage(signedIn, model);
        }
        redirected.addFlashAttribute("notice", copy.text("member.household.saved"));
        return REDIRECT;
    }

    /// @param signedIn the logged-in account
    /// @param model receives an empty form
    /// @return the form for adding a person
    @GetMapping("/medlem/hushall/ny")
    public String newMember(@AuthenticationPrincipal SignedIn signedIn, Model model) {
        households.forAccount(signedIn.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("form", new ContactForm("", "", "", "", ""));
        model.addAttribute("errors", FieldErrors.none());
        model.addAttribute("action", "/medlem/hushall/medlemmar");
        return MEMBER_FORM;
    }

    /// @param signedIn the logged-in account
    /// @param form the new person's details
    /// @param model receives field errors
    /// @param redirected receives confirmation
    /// @return household management or the invalid form
    @PostMapping("/medlem/hushall/medlemmar")
    public String addMember(@AuthenticationPrincipal SignedIn signedIn, @ModelAttribute("form") ContactForm form,
            Model model, RedirectAttributes redirected) {
        try {
            households.addForAccount(signedIn.id(), form);
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            model.addAttribute("action", "/medlem/hushall/medlemmar");
            return MEMBER_FORM;
        }
        redirected.addFlashAttribute("notice", copy.text("member.household.memberAdded", form.fullName().strip()));
        return REDIRECT;
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to edit
    /// @param model receives their current details
    /// @return the contact form
    @GetMapping("/medlem/hushall/medlemmar/{memberId}")
    public String editMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId, Model model) {
        model.addAttribute("form", households.memberForAccount(signedIn.id(), memberId));
        model.addAttribute("errors", FieldErrors.none());
        model.addAttribute("action", "/medlem/hushall/medlemmar/" + memberId);
        return MEMBER_FORM;
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to edit
    /// @param form the new contact details
    /// @param model receives field errors
    /// @param redirected receives confirmation
    /// @return household management or the invalid form
    @PostMapping("/medlem/hushall/medlemmar/{memberId}")
    public String updateMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId,
            @ModelAttribute("form") ContactForm form, Model model, RedirectAttributes redirected) {
        try {
            households.updateForAccount(signedIn.id(), memberId, form);
        } catch (ConstraintViolationException e) {
            model.addAttribute("errors", FieldErrors.of(e));
            model.addAttribute("action", "/medlem/hushall/medlemmar/" + memberId);
            return MEMBER_FORM;
        }
        redirected.addFlashAttribute("notice", copy.text("member.household.memberSaved"));
        return REDIRECT;
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to remove, possibly the caller
    /// @param model receives the person's name
    /// @return the confirmation page
    @GetMapping("/medlem/hushall/medlemmar/{memberId}/ta-bort")
    public String removeForm(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId, Model model) {
        model.addAttribute("fullName", households.memberForAccount(signedIn.id(), memberId).fullName());
        model.addAttribute("memberId", memberId);
        return "member/householdRemove";
    }

    /// @param signedIn the logged-in account
    /// @param memberId the person to remove, possibly the caller
    /// @param redirected receives confirmation
    /// @return the member overview, including when the caller has just left
    @PostMapping("/medlem/hushall/medlemmar/{memberId}/ta-bort")
    public String remove(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId,
            RedirectAttributes redirected) {
        households.removeForAccount(signedIn.id(), memberId);
        redirected.addFlashAttribute("notice", copy.text("member.household.memberRemoved"));
        return "redirect:/medlem";
    }

    /// @param response receives 404 for a missing household or a member outside it
    /// @throws IOException if the response cannot be written
    @ExceptionHandler({NoSuchMember.class, NoSuchHousehold.class})
    public void missing(HttpServletResponse response) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private String householdPage(SignedIn signedIn, Model model) {
        model.addAttribute("household", households.forAccount(signedIn.id()).orElse(null));
        model.addAttribute("ownMemberId", members.findByAccount(signedIn.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).id());
        return "member/household";
    }
}
