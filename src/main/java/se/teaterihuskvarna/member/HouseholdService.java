package se.teaterihuskvarna.member;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/// Households (R019): administrators manage the register, and members create
/// and manage their own household. Member operations derive the household
/// from the signed-in account and never accept a household id from the caller.
@Service
@Validated
@Transactional(readOnly = true)
public class HouseholdService {

    private final HouseholdRepository households;
    private final MemberRepository members;
    private final AccountRepository accounts;
    private final InvitationRepository invitations;
    private final ApplicationEventPublisher events;

    HouseholdService(HouseholdRepository households, MemberRepository members, AccountRepository accounts,
            InvitationRepository invitations, ApplicationEventPublisher events) {
        this.households = households;
        this.members = members;
        this.accounts = accounts;
        this.invitations = invitations;
        this.events = events;
    }

    /// @return every household by name, each with its members
    public List<HouseholdDetails> list() {
        List<HouseholdDetails> list = new ArrayList<>();
        for (Household household : households.findAllByName()) {
            list.add(details(household, members.findByHousehold(household.getId()), accounts, invitations));
        }
        return list;
    }

    /// @param form the new household's name
    /// @return the household as stored, with no members yet
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public HouseholdDetails create(@Valid NewHousehold form) {
        Household household = households.save(new Household(form.name().strip()));
        return new HouseholdDetails(household.getId(), household.getName(), List.of());
    }

    /// The household of the logged-in member, for `/medlem`.
    ///
    /// @param accountId the logged-in account's id
    /// @return the member's household with its members, or empty when the
    ///         member is in none or the account is gone
    public Optional<HouseholdDetails> forAccount(long accountId) {
        return accounts.findById(accountId)
                .map(account -> account.getMember().getHousehold())
                .map(household -> details(household, members.findByHousehold(household.getId()), accounts,
                        invitations));
    }

    /// Creates a household and puts the signed-in member in it atomically.
    ///
    /// @param accountId the signed-in account
    /// @param form the household name
    /// @return the household with the caller as its first member
    /// @throws AlreadyInHousehold if the caller already belongs to a household
    @Transactional
    public HouseholdDetails createForAccount(long accountId, @Valid NewHousehold form) {
        Account account = accounts.findById(accountId).orElseThrow(NoSuchMember::new);
        Member member = members.findForUpdate(account.getMember().getId()).orElseThrow(NoSuchMember::new);
        if (member.getHousehold() != null) {
            throw new AlreadyInHousehold();
        }
        Household household = households.save(new Household(form.name().strip()));
        member.setHousehold(household);
        events.publishEvent(new MemberChanged(member.getId()));
        return ownDetails(household);
    }

    /// @param accountId the signed-in account
    /// @param form the household's new name
    /// @return the renamed household
    @Transactional
    public HouseholdDetails renameForAccount(long accountId, @Valid NewHousehold form) {
        Household household = ownHousehold(accountId);
        household.setName(form.name().strip());
        return ownDetails(household);
    }

    /// Adds a person without an account. An invitation can give them one later.
    ///
    /// @param accountId the signed-in account
    /// @param form the new member's name and contact details
    /// @return the new member's id
    @Transactional
    public long addForAccount(long accountId, @Valid ContactForm form) {
        Household household = ownHousehold(accountId);
        Member member = new Member(form.fullName().strip());
        member.setContact(form.contact());
        member.setHousehold(household);
        members.save(member);
        events.publishEvent(new MemberChanged(member.getId()));
        return member.getId();
    }

    /// @param accountId the signed-in account
    /// @param memberId a member of the caller's household
    /// @return the member's editable contact details, without login email
    public ContactForm memberForAccount(long accountId, long memberId) {
        return contact(householdMember(accountId, memberId));
    }

    /// Edits a household member even when they have an account. Login email
    /// remains under administrator control.
    ///
    /// @param accountId the signed-in account
    /// @param memberId a member of the caller's household
    /// @param form the new name and contact details
    /// @return the saved contact details
    @Transactional
    public ContactForm updateForAccount(long accountId, long memberId, @Valid ContactForm form) {
        Member member = householdMember(accountId, memberId);
        member.setFullName(form.fullName().strip());
        member.setContact(form.contact());
        events.publishEvent(new MemberChanged(memberId));
        return contact(member);
    }

    /// Removes household membership only. The member, account, sessions and
    /// fee history remain. The caller may leave their own household too.
    ///
    /// @param accountId the signed-in account
    /// @param memberId a member of the caller's household
    @Transactional
    public void removeForAccount(long accountId, long memberId) {
        Member member = householdMember(accountId, memberId);
        member.setHousehold(null);
        events.publishEvent(new MemberChanged(memberId));
    }

    private Household ownHousehold(long accountId) {
        Account account = accounts.findById(accountId).orElseThrow(NoSuchMember::new);
        Household household = account.getMember().getHousehold();
        if (household == null) {
            throw new NoSuchHousehold();
        }
        return household;
    }

    private Member householdMember(long accountId, long memberId) {
        Household household = ownHousehold(accountId);
        Member member = members.findById(memberId).orElseThrow(NoSuchMember::new);
        if (member.getHousehold() == null || !household.getId().equals(member.getHousehold().getId())) {
            throw new NoSuchMember();
        }
        return member;
    }

    private HouseholdDetails ownDetails(Household household) {
        return details(household, members.findByHousehold(household.getId()), accounts, invitations);
    }

    private static ContactForm contact(Member member) {
        ContactDetails contact = member.getContact();
        return new ContactForm(member.getFullName(), contact.phone(), contact.address(), contact.postalCode(),
                contact.city());
    }

    static HouseholdDetails details(Household household, List<Member> inIt, AccountRepository accounts,
            InvitationRepository invitations) {
        List<Long> ids = inIt.stream().map(Member::getId).toList();
        Set<Long> withAccount = new HashSet<>();
        Map<Long, Instant> invited = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Account account : accounts.findByMembers(ids)) {
                withAccount.add(account.getMember().getId());
            }
            for (Invitation invitation : invitations.findByMemberIdIn(ids)) {
                invited.put(invitation.getMemberId(), invitation.getExpiresAt());
            }
        }
        List<HouseholdMember> list = new ArrayList<>();
        for (Member member : inIt) {
            list.add(new HouseholdMember(member.getId(), member.getFullName(), withAccount.contains(member.getId()),
                    invited.get(member.getId())));
        }
        return new HouseholdDetails(household.getId(), household.getName(), list);
    }
}
