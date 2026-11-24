package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Year;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import org.springframework.web.util.UriComponentsBuilder;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.AccountNeedsEmail;
import se.teaterihuskvarna.member.EmailTaken;
import se.teaterihuskvarna.member.FeeAlreadyMarked;
import se.teaterihuskvarna.member.FeeFilter;
import se.teaterihuskvarna.member.FeeKind;
import se.teaterihuskvarna.member.FeeMark;
import se.teaterihuskvarna.member.FeeService;
import se.teaterihuskvarna.member.HouseholdOwnerForm;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.InvitationRequest;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberFile;
import se.teaterihuskvarna.member.MemberForm;
import se.teaterihuskvarna.member.MemberHasAccount;
import se.teaterihuskvarna.member.MemberId;
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
    /// The fee year is the year in Sweden by the application's clock, as the fee ledger counts it.
    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    /// The register's `avgift` parameter for each choice of fee, in Swedish like the path.
    private static final String PAID = "betald";
    private static final String UNPAID = "obetald";

    private final MemberService members;
    private final HouseholdService households;
    private final FeeService fees;
    private final InvitationService invitations;
    private final Copy copy;
    private final Clock clock;
    private final Templates templates;

    MemberRegisterPageController(MemberService members, HouseholdService households, FeeService fees,
            InvitationService invitations, Copy copy, Clock clock, Templates templates) {
        this.members = members;
        this.households = households;
        this.fees = fees;
        this.invitations = invitations;
        this.copy = copy;
        this.clock = clock;
        this.templates = templates;
    }

    /// @param query    what to search for, or nothing to list everyone
    /// @param fee      `betald` or `obetald` for only those whose fee this year is or is not paid
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the register: the matches, the query and the choices of fee
    @GetMapping(LIST)
    public View list(@RequestParam(name = "q", required = false) @Nullable String query,
            @RequestParam(name = "avgift", required = false) @Nullable String fee, CsrfField csrf,
            Feedback feedback) {
        FeeFilter filter = filter(fee);
        String year = String.valueOf(Year.now(clock.withZone(SWEDEN)).getValue());
        List<RegisterFilter> filters = List.of(
                new RegisterFilter(copy.text("register.filter.all"), listUrl(query, null), filter == FeeFilter.ALL),
                new RegisterFilter(copy.text("register.filter.unpaid", year), listUrl(query, UNPAID),
                        filter == FeeFilter.UNPAID),
                new RegisterFilter(copy.text("register.filter.paid", year), listUrl(query, PAID),
                        filter == FeeFilter.PAID));
        return HtmlPage.of(templates.adminMembers(copy, csrf, query == null ? "" : query,
                filter == FeeFilter.ALL ? "" : fee, filters, members.search(query, filter), feedback));
    }

    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the form for a new member, empty, with the households to choose from
    @GetMapping(LIST + "/ny")
    public View newMember(CsrfField csrf, Feedback feedback) {
        return newPage(MemberForm.empty(), FieldErrors.none(), csrf, feedback);
    }

    /// @param form     the new member
    /// @param binding  what Spring could not read of the form
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return a redirect to the new member's page, or the form again with what was wrong
    @PostMapping(LIST)
    public View add(@ModelAttribute("form") MemberForm form, BindingResult binding, CsrfField csrf,
            Feedback feedback) {
        if (binding.hasErrors()) {
            return newPage(form, FieldErrors.of(binding, copy, "member"), csrf, feedback);
        }
        MemberDetails added;
        try {
            added = members.add(form);
        } catch (ConstraintViolationException e) {
            return newPage(form, FieldErrors.of(e), csrf, feedback);
        } catch (EmailTaken e) {
            return newPage(form, FieldErrors.none(), csrf,
                    feedback.withError(copy.text("register.error.emailTaken")));
        } catch (NoSuchHousehold e) {
            return newPage(form, FieldErrors.none(), csrf,
                    feedback.withError(copy.text("register.error.noSuchHousehold")));
        }
        return redirect(added.id()).notice(copy.text("register.added", added.fullName()));
    }

    /// @param id       the member
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the member's page: their file and a form with their current values
    @GetMapping(LIST + "/{id}")
    public View member(@PathVariable MemberId id, CsrfField csrf, Feedback feedback) {
        MemberFile file = file(id);
        return memberPage(file, MemberForm.of(file.member()), FieldErrors.none(), csrf, feedback);
    }

    /// @param id       the member
    /// @param form     the new values
    /// @param binding  what Spring could not read of the form
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return a redirect to the member's page, or the page again with what was wrong
    @PostMapping(LIST + "/{id}")
    public View update(@PathVariable MemberId id, @ModelAttribute("form") MemberForm form, BindingResult binding,
            CsrfField csrf, Feedback feedback) {
        if (binding.hasErrors()) {
            return memberPage(file(id), form, FieldErrors.of(binding, copy, "member"), csrf, feedback);
        }
        String error;
        try {
            members.update(id, form);
            return redirect(id).notice(copy.text("register.saved"));
        } catch (ConstraintViolationException e) {
            return memberPage(file(id), form, FieldErrors.of(e), csrf, feedback);
        } catch (EmailTaken e) {
            error = copy.text("register.error.emailTaken");
        } catch (AccountNeedsEmail e) {
            error = copy.text("register.error.accountNeedsEmail");
        } catch (NoSuchHousehold e) {
            error = copy.text("register.error.noSuchHousehold");
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
        return memberPage(file(id), form, FieldErrors.none(), csrf, feedback.withError(error));
    }

    /// The confirmation step before a delete.
    ///
    /// @param id       the member
    /// @param csrf     the hidden field of the page's form
    /// @param feedback what the request before left for this page
    /// @return the page that asks whether to delete
    @GetMapping(LIST + "/{id}/ta-bort")
    public View confirmDelete(@PathVariable MemberId id, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminMemberDelete(copy, csrf, file(id), feedback));
    }

    /// @param id the member
    /// @return a redirect to the register, with the outcome
    @PostMapping(LIST + "/{id}/ta-bort")
    public View delete(@PathVariable MemberId id) {
        try {
            members.delete(id);
        } catch (NoSuchMember e) {
            return Redirect.to(LIST).error(copy.text("register.error.noSuchMember"));
        }
        return Redirect.to(LIST).notice(copy.text("register.deleted"));
    }

    /// @param signedIn the administrator, recorded as the one who marked it
    /// @param id       the member who paid
    /// @param kind     `INDIVIDUAL` or `HOUSEHOLD`
    /// @param kronor   the amount in whole kronor, or blank for the configured amount
    /// @param back     `lista` when the button was in the register, which is where to go back to
    /// @param query    the register's search, to go back to
    /// @param fee      the register's choice of fee, to go back to
    /// @return a redirect to the member's page, or to the register the button was in, with the outcome
    @PostMapping(LIST + "/{id}/avgift")
    public View markPaid(@AuthenticationPrincipal SignedIn signedIn, @PathVariable MemberId id,
            @RequestParam(required = false) @Nullable String kind,
            @RequestParam(required = false) @Nullable String kronor,
            @RequestParam(name = "tillbaka", required = false) @Nullable String back,
            @RequestParam(name = "q", required = false) @Nullable String query,
            @RequestParam(name = "avgift", required = false) @Nullable String fee) {
        Redirect to = "lista".equals(back) ? Redirect.to(listUrl(query, fee)) : redirect(id);
        try {
            fees.markPaid(id, new FeeMark(kind(kind), ore(kronor)), signedIn.administratorId());
        } catch (ConstraintViolationException e) {
            return to.error(MemberPageController.firstMessage(e));
        } catch (FeeAlreadyMarked e) {
            return to.error(copy.text("fee.error.alreadyMarked"));
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
        return to.notice("lista".equals(back)
                ? copy.text("fee.markedFor", file(id).member().fullName())
                : copy.text("fee.marked"));
    }

    /// @param id the member whose mark to undo
    /// @return a redirect to the member's page, with the outcome
    @PostMapping(LIST + "/{id}/avgift/angra")
    public View undoFee(@PathVariable MemberId id) {
        try {
            fees.undo(id);
        } catch (NoSuchFee e) {
            return redirect(id).error(copy.text("fee.error.noSuch"));
        }
        return redirect(id).notice(copy.text("fee.undone"));
    }

    /// @param id    the member to invite
    /// @param email the address to send the invitation to
    /// @return a redirect to the member's page, with the outcome
    @PostMapping(LIST + "/{id}/inbjudan")
    public View invite(@PathVariable MemberId id, @RequestParam(defaultValue = "") String email) {
        try {
            invitations.invite(id, new InvitationRequest(email));
        } catch (ConstraintViolationException e) {
            return redirect(id).error(MemberPageController.firstMessage(e));
        } catch (MemberHasAccount e) {
            return redirect(id).error(copy.text("invitation.error.hasAccount"));
        } catch (EmailTaken e) {
            return redirect(id).error(copy.text("invitation.error.emailTaken"));
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
        return redirect(id).notice(copy.text("invitation.sent", email.strip()));
    }

    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return the households page, with an empty form
    @GetMapping(HOUSEHOLDS)
    public View households(CsrfField csrf, Feedback feedback) {
        return householdsPage(new NewHousehold(""), FieldErrors.none(), csrf, feedback);
    }

    /// @param form     the new household's name
    /// @param csrf     the hidden field of the page's forms
    /// @param feedback what the request before left for this page
    /// @return a redirect to the households page, or the page again with what was wrong
    @PostMapping(HOUSEHOLDS)
    public View createHousehold(@ModelAttribute("form") NewHousehold form, CsrfField csrf, Feedback feedback) {
        try {
            households.create(form);
        } catch (ConstraintViolationException e) {
            return householdsPage(form, FieldErrors.of(e), csrf, feedback);
        }
        return Redirect.to(HOUSEHOLDS).notice(copy.text("household.created", form.name().strip()));
    }

    /// @param id the household
    /// @param form the next member owner, or administrator management
    /// @param binding what Spring could not read of the form
    /// @return the household list, with confirmation or an invalid selection message
    @PostMapping(HOUSEHOLDS + "/{id}/agare")
    public View changeHouseholdOwner(@PathVariable long id, @ModelAttribute HouseholdOwnerForm form,
            BindingResult binding) {
        if (binding.hasErrors()) {
            return Redirect.to(HOUSEHOLDS).error(copy.text("household.owner.memberId.invalid"));
        }
        try {
            households.changeOwner(id, form);
        } catch (ConstraintViolationException | NoSuchMember e) {
            return Redirect.to(HOUSEHOLDS).error(copy.text("household.owner.memberId.invalid"));
        } catch (NoSuchHousehold e) {
            throw notFound(e);
        }
        return Redirect.to(HOUSEHOLDS).notice(copy.text("household.owner.saved"));
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

    private View newPage(MemberForm form, FieldErrors errors, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminMemberNew(copy, csrf, form, errors, households.list(), feedback));
    }

    private View memberPage(MemberFile file, MemberForm form, FieldErrors errors, CsrfField csrf,
            Feedback feedback) {
        return HtmlPage.of(templates.adminMember(copy, csrf, file, form, errors, households.list(), feedback));
    }

    private View householdsPage(NewHousehold form, FieldErrors errors, CsrfField csrf, Feedback feedback) {
        return HtmlPage.of(templates.adminHouseholds(copy, csrf, households.list(), form, errors, feedback));
    }

    private MemberFile file(MemberId id) {
        try {
            return members.find(id);
        } catch (NoSuchMember e) {
            throw notFound(e);
        }
    }

    private static ResponseStatusException notFound(RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, null, cause);
    }

    private static Redirect redirect(MemberId id) {
        return Redirect.to(LIST + "/" + id);
    }

    private static FeeFilter filter(@Nullable String fee) {
        if (PAID.equals(fee)) {
            return FeeFilter.PAID;
        }
        return UNPAID.equals(fee) ? FeeFilter.UNPAID : FeeFilter.ALL;
    }

    /// The register with a search and a choice of fee, each left out when there is none.
    ///
    /// The values go in as URI variables, which `encode()` encodes strictly. A
    /// value written straight into the query keeps a `+`, which the next request
    /// reads as a space, and keeps braces, which a redirect reads as a URI
    /// template and fails on.
    private static String listUrl(@Nullable String query, @Nullable String fee) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath(LIST);
        Map<String, String> values = new HashMap<>();
        if (query != null && !query.isBlank()) {
            url.queryParam("q", "{q}");
            values.put("q", query);
        }
        FeeFilter filter = filter(fee);
        if (filter != FeeFilter.ALL) {
            url.queryParam("avgift", "{avgift}");
            values.put("avgift", filter == FeeFilter.PAID ? PAID : UNPAID);
        }
        return url.encode().buildAndExpand(values).toUriString();
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
