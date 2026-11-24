package se.teaterihuskvarna.member;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.login.AccountId;
import se.teaterihuskvarna.login.Email;
import se.teaterihuskvarna.login.Lifetimes;
import se.teaterihuskvarna.login.MailLife;
import se.teaterihuskvarna.login.MailSettings;
import se.teaterihuskvarna.login.Mailer;
import se.teaterihuskvarna.login.Tokens;

/// Invitations for a household member without an account to create one (R019).
///
/// An administrator can invite any member without an account. A member with an
/// account can invite the members of their own household who have none.
/// Members can also add people through [HouseholdService#addForAccount].
///
/// The link opens a page with a button, and only the button's POST creates
/// the account, so a mail scanner that follows the link uses nothing up. Nobody
/// is logged in by it; the new account logs in by link like any other.
@Service
@Validated
@Transactional(readOnly = true)
public class InvitationService {

    static final String PATH = "/inbjudan";

    private final InvitationRepository invitations;
    private final MemberRepository members;
    private final AccountRepository accounts;
    private final Mailer mailer;
    private final MessageSource messages;
    private final MailSettings mail;
    private final AssociationSettings association;
    private final Clock clock;

    InvitationService(InvitationRepository invitations, MemberRepository members, AccountRepository accounts,
            Mailer mailer, MessageSource messages, MailSettings mail, AssociationSettings association,
            Clock clock) {
        this.invitations = invitations;
        this.members = members;
        this.accounts = accounts;
        this.mailer = mailer;
        this.messages = messages;
        this.mail = mail;
        this.association = association;
        this.clock = clock;
    }

    /// An administrator invites a member. Sending again replaces the open
    /// invitation, so only the newest link works.
    ///
    /// @param memberId the member to invite
    /// @param request  the address the account will have
    /// @throws jakarta.validation.ConstraintViolationException if the address is not one
    /// @throws NoSuchMember if there is no such member
    /// @throws MemberHasAccount if the member already has an account
    /// @throws EmailTaken if another account has the address
    @Transactional
    public void invite(MemberId memberId, @Valid InvitationRequest request) {
        send(members.findById(memberId.value()).orElseThrow(NoSuchMember::new), request);
    }

    /// A member invites someone in their own household.
    ///
    /// @param accountId the logged-in account's id
    /// @param memberId  the household member to invite
    /// @param request   the address the account will have
    /// @throws jakarta.validation.ConstraintViolationException if the address is not one
    /// @throws NoSuchMember if the member is not in the caller's household, or is the caller
    /// @throws MemberHasAccount if the member already has an account
    /// @throws EmailTaken if another account has the address
    @Transactional
    public void inviteToHousehold(AccountId accountId, MemberId memberId, @Valid InvitationRequest request) {
        Account inviter = accounts.findById(accountId.value()).orElseThrow(NoSuchMember::new);
        Household household = inviter.getMember().getHousehold();
        Member invited = members.findById(memberId.value()).orElseThrow(NoSuchMember::new);
        Household theirs = invited.getHousehold();
        if (household == null || theirs == null || !household.getId().equals(theirs.getId())) {
            throw new NoSuchMember();
        }
        send(invited, request);
    }

    /// What the page behind an invitation link should show. A lookup and
    /// nothing more: mail scanners open every link, so opening one must not
    /// spend it.
    ///
    /// @param token the token from the link
    /// @return what [#accept] would answer now in place of creating the account, or empty if it would
    ///         create it
    public Optional<InvitationOutcome> refusal(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return Optional.of(InvitationOutcome.INVALID);
        }
        Optional<Invitation> found = invitations.readByTokenHash(Tokens.hash(token));
        return found.isEmpty() ? Optional.of(InvitationOutcome.INVALID) : refusalOf(found.get());
    }

    /// Uses an invitation link. The invitation is deleted whatever the outcome,
    /// so a link works once at most.
    ///
    /// @param token the token from the link
    /// @return what happened
    @Transactional
    public InvitationOutcome accept(@Nullable String token) {
        if (token == null || token.isBlank()) {
            return InvitationOutcome.INVALID;
        }
        Optional<Invitation> found = invitations.findByTokenHash(Tokens.hash(token));
        if (found.isEmpty()) {
            return InvitationOutcome.INVALID;
        }
        Invitation invitation = found.get();
        invitations.delete(invitation);
        Optional<InvitationOutcome> refusal = refusalOf(invitation);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        Member member = members.findById(invitation.getMemberId().value()).orElseThrow();
        accounts.save(new Account(member, new Email(invitation.getEmail()), clock.instant()));
        return InvitationOutcome.ACCEPTED;
    }

    /// @return why the invitation cannot become an account now, or empty if it can: it has expired, its
    ///         member is gone or has an account already, or another account has its address
    private Optional<InvitationOutcome> refusalOf(Invitation invitation) {
        long member = invitation.getMemberId().value();
        if (!invitation.getExpiresAt().isAfter(clock.instant())
                || members.findById(member).isEmpty()
                || accounts.findByMember(member).isPresent()) {
            return Optional.of(InvitationOutcome.INVALID);
        }
        if (accounts.findByEmailIgnoreCase(invitation.getEmail()).isPresent()) {
            return Optional.of(InvitationOutcome.EMAIL_TAKEN);
        }
        return Optional.empty();
    }

    private void send(Member member, InvitationRequest request) {
        if (accounts.findByMember(member.getId()).isPresent()) {
            throw new MemberHasAccount();
        }
        Email email = new Email(request.email());
        if (accounts.findByEmailIgnoreCase(email.value()).isPresent()) {
            throw new EmailTaken();
        }
        String token = Tokens.newToken();
        Instant now = clock.instant();
        Instant expires = now.plus(association.invitationLifetime());
        invitations.replace(member.getId(), email.value(), Tokens.hash(token), now, expires);
        String lifetime = Lifetimes.describe(messages, association.invitationLifetime());
        mailer.send(
                email,
                messages.getMessage("invitation.mail.subject", null, Swedish.LOCALE),
                messages.getMessage("invitation.mail.body",
                        new Object[] {member.getFullName(), mail.link(PATH, token), lifetime, email.value()},
                        Swedish.LOCALE),
                // Not sent once the invitation, and so the link, has expired.
                MailLife.until(expires));
    }
}
