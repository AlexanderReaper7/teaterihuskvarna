package se.teaterihuskvarna.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.FeeMark;
import se.teaterihuskvarna.member.FeeService;
import se.teaterihuskvarna.member.HouseholdDetails;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.InvitationRequest;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberFile;
import se.teaterihuskvarna.member.MemberForm;
import se.teaterihuskvarna.member.MemberList;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NewHousehold;

/// The member register over HTTP: the same search, add, edit, delete, fees,
/// invitations, households and export the `/admin/medlemmar` pages offer
/// (R018, R019, R021). Errors become statuses in [ProblemResponses].
@RestController
public class MemberRegisterController {

    private final MemberService members;
    private final HouseholdService households;
    private final FeeService fees;
    private final InvitationService invitations;

    MemberRegisterController(MemberService members, HouseholdService households, FeeService fees,
            InvitationService invitations) {
        this.members = members;
        this.households = households;
        this.fees = fees;
        this.invitations = invitations;
    }

    /// @param query a case insensitive part of a name, address, phone or city, or nothing for everyone
    /// @return the first matches by name, and whether there were more
    @GetMapping("/api/admin/members")
    public MemberList search(@RequestParam(name = "q", required = false) @Nullable String query) {
        return members.search(query);
    }

    /// @param form the new member; an address gives them an account
    /// @return the member as stored
    @PostMapping("/api/admin/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDetails add(@RequestBody MemberForm form) {
        return members.add(form);
    }

    /// @param id the member
    /// @return the member with fees per year, household and open invitation
    @GetMapping("/api/admin/members/{id}")
    public MemberFile find(@PathVariable long id) {
        return members.find(id);
    }

    /// @param id   the member
    /// @param form every field, the address and household included
    /// @return the member as stored
    @PutMapping("/api/admin/members/{id}")
    public MemberDetails update(@PathVariable long id, @RequestBody MemberForm form) {
        return members.update(id, form);
    }

    /// Deletes the member with their account; fee payments stay, anonymised.
    ///
    /// @param id the member
    @DeleteMapping("/api/admin/members/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        members.delete(id);
    }

    /// @param signedIn the administrator, recorded as the one who marked it
    /// @param id       the member who paid
    /// @param mark     the kind, and the amount in öre if not the configured one
    @PostMapping("/api/admin/members/{id}/fee")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markPaid(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long id,
            @RequestBody FeeMark mark) {
        fees.markPaid(id, mark, signedIn.id());
    }

    /// @param id the member whose mark for this year to undo
    @DeleteMapping("/api/admin/members/{id}/fee")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undoFee(@PathVariable long id) {
        fees.undo(id);
    }

    /// @param id      the member to invite
    /// @param request the address the invitation goes to
    @PostMapping("/api/admin/members/{id}/invitation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void invite(@PathVariable long id, @RequestBody InvitationRequest request) {
        invitations.invite(id, request);
    }

    /// @return every household by name, with its members
    @GetMapping("/api/admin/households")
    public List<HouseholdDetails> households() {
        return households.list();
    }

    /// @param form the new household's name
    /// @return the household as stored
    @PostMapping("/api/admin/households")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDetails createHousehold(@RequestBody NewHousehold form) {
        return households.create(form);
    }

    /// @return the whole register as a CSV download, as `/admin/medlemmar.csv` gives it
    @GetMapping("/api/admin/members.csv")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("medlemsregister.csv").build().toString())
                .body(members.exportCsv().getBytes(StandardCharsets.UTF_8));
    }
}
