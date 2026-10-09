package se.teaterihuskvarna.web;

import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import se.teaterihuskvarna.member.AccountNeedsEmail;
import se.teaterihuskvarna.member.EmailTaken;
import se.teaterihuskvarna.member.FeeAlreadyMarked;
import se.teaterihuskvarna.member.FeeKind;
import se.teaterihuskvarna.member.FeeMark;
import se.teaterihuskvarna.member.FeeService;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.HouseholdOwnerForm;
import se.teaterihuskvarna.member.InvitationRequest;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberFile;
import se.teaterihuskvarna.member.MemberForm;
import se.teaterihuskvarna.member.MemberHasAccount;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NewHousehold;
import se.teaterihuskvarna.member.NoSuchFee;
import se.teaterihuskvarna.member.NoSuchHousehold;
import se.teaterihuskvarna.member.NoSuchMember;

/// The member register for administrators (R018, R019, R021): search, add, edit
/// and delete members, mark fees, send invitations, manage households and
/// export the register. Spring Security lets only an administrator reach
/// `/admin/**`.
///
/// A failed form shows the page again with the typed values; a button with
/// nothing typed redirects back with a flash message, as on `/admin`.
@Controller
public class MemberRegisterPageController {

    private static final String LIST = "/admin/medlemmar";
    private static final String HOUSEHOLDS = "/admin/hushall";

    private final MemberService members;
    private final HouseholdService households;
    private final FeeService fees;
    private final InvitationService invitations;
    private final Copy copy;

    MemberRegisterPageController(MemberService members, HouseholdService households, FeeService fees,
            InvitationService invitations, Copy copy) {
        this.members = members;
        this.households = households;
        this.fees = fees;
        this.invitations = invitations;
        this.copy = copy;
    }

    /// @param query what to search for, or nothing to list everyone
    /// @param model receives the matches and the query
    /// @return the register
    @GetMapping(LIST)
    public String list(@RequestParam(name = "q", required = false) @Nullable String query, Model model) {
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("list", members.search(query));
        return "admin/members";
    }

    /// @param model receives an empty form and the households to choose from
    /// @return the form for a new member
    @GetMapping(LIST + "/ny")
    public String newMember(Model model) {
        return newPage(MemberForm.empty(), FieldErrors.none(), null, model);
    }

    /// @param form       the new member
    /// @param model      receives the form again when the add fails
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the new member's page, or the form again with what was wrong
    @PostMapping(LIST)
    public String add(@ModelAttribute("form") MemberForm form, Model model, RedirectAttributes redirected) {
        MemberDetails added;
        try {
            added = members.add(form);
        } catch (ConstraintViolationException e) {
            return newPage(form, FieldErrors.of(e), null, model);
        } catch (EmailTaken e) {
            return newPage(form, FieldErrors.none(), copy.text("register.error.emailTaken"), model);
        } catch (NoSuchHousehold e) {
            return newPage(form, FieldErrors.none(), copy.text("register.error.noSuchHousehold"), model);
        }
        redirected.addFlashAttribute("notice", copy.text("register.added", added.fullName()));
        return redirect(added.id());
    }

    /// @param id    the member
    /// @param model receives the member's file and a form with their current values
    /// @return the member's page
    @GetMapping(LIST + "/{id}")
    public String member(@PathVariable long id, Model model) {
        MemberFile file = file(id);
        return memberPage(file, MemberForm.of(file.member()), FieldErrors.none(), null, model);
    }

    /// @param id         the member
    /// @param form       the new values
    /// @param model      receives the page again when the change fails
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the member's page, or the page again with what was wrong
    @PostMapping(LIST + "/{id}")
    public String update(@PathVariable long id, @ModelAttribute("form") MemberForm form, Model model,
            RedirectAttributes redirected) {
        try {
            members.update(id, form);
        } catch (ConstraintViolationException e) {
            return memberPage(file(id), form, FieldErrors.of(e), null, model);
        } catch (EmailTaken e) {
            return memberPage(file(id), form, FieldErrors.none(), copy.text("register.error.emailTaken"), model);
        } catch (AccountNeedsEmail e) {
            return memberPage(file(id), form, FieldErrors.none(), copy.text("register.error.accountNeedsEmail"),
                    model);
        } catch (NoSuchHousehold e) {
            return memberPage(file(id), form, FieldErrors.none(), copy.text("register.error.noSuchHousehold"),
                    model);
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
        redirected.addFlashAttribute("notice", copy.text("register.saved"));
        return redirect(id);
    }

    /// The confirmation step before a delete.
    ///
    /// @param id    the member
    /// @param model receives the member's file
    /// @return the page that asks whether to delete
    @GetMapping(LIST + "/{id}/ta-bort")
    public String confirmDelete(@PathVariable long id, Model model) {
        model.addAttribute("file", file(id));
        return "admin/memberDelete";
    }

    /// @param id         the member
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the register
    @PostMapping(LIST + "/{id}/ta-bort")
    public String delete(@PathVariable long id, RedirectAttributes redirected) {
        try {
            members.delete(id);
        } catch (NoSuchMember e) {
            redirected.addFlashAttribute("error", copy.text("register.error.noSuchMember"));
            return "redirect:" + LIST;
        }
        redirected.addFlashAttribute("notice", copy.text("register.deleted"));
        return "redirect:" + LIST;
    }

    /// @param signedIn   the administrator, recorded as the one who marked it
    /// @param id         the member who paid
    /// @param kind       `INDIVIDUAL` or `HOUSEHOLD`
    /// @param kronor     the amount in whole kronor, or blank for the configured amount
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the member's page
    @PostMapping(LIST + "/{id}/avgift")
    public String markPaid(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            @RequestParam(required = false) @Nullable String kind,
            @RequestParam(required = false) @Nullable String kronor, RedirectAttributes redirected) {
        try {
            fees.markPaid(id, new FeeMark(kind(kind), ore(kronor)), signedIn.id());
        } catch (ConstraintViolationException e) {
            redirected.addFlashAttribute("error", MemberPageController.firstMessage(e));
            return redirect(id);
        } catch (FeeAlreadyMarked e) {
            redirected.addFlashAttribute("error", copy.text("fee.error.alreadyMarked"));
            return redirect(id);
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
        redirected.addFlashAttribute("notice", copy.text("fee.marked"));
        return redirect(id);
    }

    /// @param id         the member whose mark to undo
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the member's page
    @PostMapping(LIST + "/{id}/avgift/angra")
    public String undoFee(@PathVariable long id, RedirectAttributes redirected) {
        try {
            fees.undo(id);
        } catch (NoSuchFee e) {
            redirected.addFlashAttribute("error", copy.text("fee.error.noSuch"));
            return redirect(id);
        }
        redirected.addFlashAttribute("notice", copy.text("fee.undone"));
        return redirect(id);
    }

    /// @param id         the member to invite
    /// @param email      the address to send the invitation to
    /// @param redirected receives the outcome, shown after the redirect
    /// @return a redirect to the member's page
    @PostMapping(LIST + "/{id}/inbjudan")
    public String invite(@PathVariable long id, @RequestParam(defaultValue = "") String email,
            RedirectAttributes redirected) {
        try {
            invitations.invite(id, new InvitationRequest(email));
        } catch (ConstraintViolationException e) {
            redirected.addFlashAttribute("error", MemberPageController.firstMessage(e));
            return redirect(id);
        } catch (MemberHasAccount e) {
            redirected.addFlashAttribute("error", copy.text("invitation.error.hasAccount"));
            return redirect(id);
        } catch (EmailTaken e) {
            redirected.addFlashAttribute("error", copy.text("invitation.error.emailTaken"));
            return redirect(id);
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
        redirected.addFlashAttribute("notice", copy.text("invitation.sent", email.strip()));
        return redirect(id);
    }

    /// @param model receives the households and an empty form
    /// @return the households page
    @GetMapping(HOUSEHOLDS)
    public String households(Model model) {
        return householdsPage(new NewHousehold(""), FieldErrors.none(), model);
    }

    /// @param form       the new household's name
    /// @param model      receives the page again when the create fails
    /// @param redirected receives the confirmation shown after the redirect
    /// @return a redirect to the households page, or the page again with what was wrong
    @PostMapping(HOUSEHOLDS)
    public String createHousehold(@ModelAttribute("form") NewHousehold form, Model model,
            RedirectAttributes redirected) {
        try {
            households.create(form);
        } catch (ConstraintViolationException e) {
            return householdsPage(form, FieldErrors.of(e), model);
        }
        redirected.addFlashAttribute("notice", copy.text("household.created", form.name().strip()));
        return "redirect:" + HOUSEHOLDS;
    }

    /// @param id the household
    /// @param form the next member owner, or administrator management
    /// @param redirected receives confirmation or an invalid selection message
    /// @return the household list
    @PostMapping(HOUSEHOLDS + "/{id}/agare")
    public String changeHouseholdOwner(@PathVariable long id, @ModelAttribute HouseholdOwnerForm form,
            RedirectAttributes redirected) {
        try {
            households.changeOwner(id, form);
        } catch (ConstraintViolationException | NoSuchMember e) {
            redirected.addFlashAttribute("notice", copy.text("household.owner.invalid"));
            return "redirect:" + HOUSEHOLDS;
        } catch (NoSuchHousehold e) {
            throw notFound(e);
        }
        redirected.addFlashAttribute("notice", copy.text("household.owner.saved"));
        return "redirect:" + HOUSEHOLDS;
    }

    /// The whole register as a spreadsheet file (R021).
    ///
    /// @return the CSV file as a download
    @GetMapping(LIST + ".csv")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("medlemsregister.csv").build().toString())
                .body(members.exportCsv().getBytes(StandardCharsets.UTF_8));
    }

    private String newPage(MemberForm form, FieldErrors errors, @Nullable String error, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("households", households.list());
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "admin/memberNew";
    }

    private String memberPage(MemberFile file, MemberForm form, FieldErrors errors, @Nullable String error,
            Model model) {
        model.addAttribute("file", file);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("households", households.list());
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "admin/member";
    }

    private String householdsPage(NewHousehold form, FieldErrors errors, Model model) {
        model.addAttribute("households", households.list());
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        return "admin/households";
    }

    private MemberFile file(long id) {
        try {
            return members.find(id);
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
    }

    private static ResponseStatusException notFound(RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, null, cause);
    }

    private static String redirect(long id) {
        return "redirect:" + LIST + "/" + id;
    }

    /// An unknown kind becomes null, which the service refuses with its own
    /// message, the same one a missing kind gets.
    private static @Nullable FeeKind kind(@Nullable String kind) {
        for (FeeKind known : FeeKind.values()) {
            if (known.name().equals(kind)) {
                return known;
            }
        }
        return null;
    }

    /// The page asks for whole kronor and the service stores öre. Anything
    /// that is not a whole number becomes -1, which the service refuses with
    /// its message for an invalid amount.
    private static @Nullable Integer ore(@Nullable String kronor) {
        if (kronor == null || kronor.isBlank()) {
            return null;
        }
        try {
            return Math.multiplyExact(Integer.parseInt(kronor.strip()), 100);
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}
