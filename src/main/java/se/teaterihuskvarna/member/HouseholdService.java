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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/// Households (R019): an administrator creates one and lists them, and a member
/// sees their own. Moving a member into or out of a household is part of
/// editing the member, [MemberService#update], and adding a member straight
/// into a household is part of [MemberService#add].
@Service
@Validated
@Transactional(readOnly = true)
public class HouseholdService {

    private final HouseholdRepository households;
    private final MemberRepository members;
    private final AccountRepository accounts;
    private final InvitationRepository invitations;

    HouseholdService(HouseholdRepository households, MemberRepository members, AccountRepository accounts,
            InvitationRepository invitations) {
        this.households = households;
        this.members = members;
        this.accounts = accounts;
        this.invitations = invitations;
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
