package se.teaterihuskvarna.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.login.SignedIn;
import se.teaterihuskvarna.member.ContactForm;
import se.teaterihuskvarna.member.HouseholdDetails;
import se.teaterihuskvarna.member.HouseholdService;
import se.teaterihuskvarna.member.InvitationRequest;
import se.teaterihuskvarna.member.InvitationService;
import se.teaterihuskvarna.member.MemberDetails;
import se.teaterihuskvarna.member.MemberService;
import se.teaterihuskvarna.member.NewHousehold;

/// The logged-in member's own details, household and invitations, as `/medlem`
/// shows them.
@RestController
public class MemberController {

    private final MemberService members;
    private final HouseholdService households;
    private final InvitationService invitations;

    MemberController(MemberService members, HouseholdService households, InvitationService invitations) {
        this.members = members;
        this.households = households;
        this.invitations = invitations;
    }

    /// @param signedIn the logged-in account
    /// @return 200 with the member, or 404 if the member was removed while the session lived on
    @GetMapping("/api/member")
    public ResponseEntity<MemberDetails> member(@AuthenticationPrincipal SignedIn signedIn) {
        return ResponseEntity.of(members.findByAccount(signedIn.id()));
    }

    /// Changes everything but the address, which only an administrator changes (R012).
    ///
    /// @param signedIn the logged-in account
    /// @param form     the new values
    /// @return the member as stored
    @PutMapping("/api/member/contact")
    public MemberDetails updateContact(@AuthenticationPrincipal SignedIn signedIn, @RequestBody ContactForm form) {
        return members.updateContact(signedIn.id(), form);
    }

    /// @param signedIn the logged-in account
    /// @return 200 with the member's household, or 404 when the member is in none
    @GetMapping("/api/member/household")
    public ResponseEntity<HouseholdDetails> household(@AuthenticationPrincipal SignedIn signedIn) {
        return ResponseEntity.of(households.forAccount(signedIn.id()));
    }

    /// @param signedIn the logged-in account
    /// @param form the household name
    /// @return the new household containing the caller
    @PostMapping("/api/member/household")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDetails createHousehold(@AuthenticationPrincipal SignedIn signedIn,
            @RequestBody NewHousehold form) {
        return households.createForAccount(signedIn.id(), form);
    }

    /// @param signedIn the logged-in account
    /// @param form the household's new name
    /// @return the renamed household
    @PutMapping("/api/member/household")
    public HouseholdDetails renameHousehold(@AuthenticationPrincipal SignedIn signedIn,
            @RequestBody NewHousehold form) {
        return households.renameForAccount(signedIn.id(), form);
    }

    /// @param signedIn the logged-in account
    /// @param form the new person's name and contact details
    /// @return the new member's id, without creating an account
    @PostMapping("/api/member/household/members")
    @ResponseStatus(HttpStatus.CREATED)
    public long addHouseholdMember(@AuthenticationPrincipal SignedIn signedIn, @RequestBody ContactForm form) {
        return households.addForAccount(signedIn.id(), form);
    }

    /// @param signedIn the logged-in account
    /// @param memberId a person in the caller's household
    /// @return the person's editable contact details
    @GetMapping("/api/member/household/members/{memberId}")
    public ContactForm householdMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId) {
        return households.memberForAccount(signedIn.id(), memberId);
    }

    /// @param signedIn the logged-in account
    /// @param memberId a person in the caller's household
    /// @param form the new name and contact details
    /// @return the saved contact details
    @PutMapping("/api/member/household/members/{memberId}")
    public ContactForm updateHouseholdMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId,
            @RequestBody ContactForm form) {
        return households.updateForAccount(signedIn.id(), memberId, form);
    }

    /// The caller can remove themselves to leave the household.
    ///
    /// @param signedIn the logged-in account
    /// @param memberId a person in the caller's household
    /// @param successorMemberId the next owner if the caller leaves, or null
    @DeleteMapping("/api/member/household/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeHouseholdMember(@AuthenticationPrincipal SignedIn signedIn, @PathVariable long memberId,
            @RequestParam(required = false) @Nullable Long successorMemberId) {
        households.removeForAccount(signedIn.id(), memberId, successorMemberId);
    }

    /// Invites someone in the caller's household who has no account (R019).
    ///
    /// @param signedIn   the logged-in account
    /// @param invitation who to invite, and at which address
    @PostMapping("/api/member/household/invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void invite(@AuthenticationPrincipal SignedIn signedIn,
            @RequestBody @Valid HouseholdInvitation invitation) {
        invitations.inviteToHousehold(signedIn.id(), invitation.memberId(), new InvitationRequest(
                invitation.email() == null ? "" : invitation.email()));
    }

    /// The body of a household invitation. The address is checked by the service.
    ///
    /// @param memberId the household member to invite
    /// @param email    the address the invitation goes to
    public record HouseholdInvitation(@NotNull Long memberId, @Nullable String email) {
    }
}
