package se.teaterihuskvarna.member;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/// Computes fee statuses, the rule [FeeStatus] states, from the `fee` table,
/// and gives a household fee its household when the payer gains one after
/// paying. The services in this package call it inside their own
/// transactions. It reads a member's address from their account itself, for
/// the payment instruction, so no caller can pass the wrong one.
@Component
class FeeLedger {

    /// The association is in Huskvarna, so "this year" is the year there,
    /// whatever zone the container or the application's clock is in. New
    /// Year's night is the only time the two can differ.
    static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    private final FeeRepository fees;
    private final AccountRepository accounts;
    private final MemberRepository members;
    private final AssociationSettings association;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    FeeLedger(FeeRepository fees, AccountRepository accounts, MemberRepository members,
            AssociationSettings association, ApplicationEventPublisher events, Clock clock) {
        this.fees = fees;
        this.accounts = accounts;
        this.members = members;
        this.association = association;
        this.events = events;
        this.clock = clock;
    }

    /// Read from the application's clock, never the system's, so a test can
    /// stop the clock on New Year's night.
    ///
    /// @return the current year in Sweden
    int currentYear() {
        return Year.now(clock.withZone(SWEDEN)).getValue();
    }

    /// A member has gained a household. A household fee the member paid for
    /// this year while in no household names none, and now names this one, so
    /// it covers everyone in it. The contact of each other member of the
    /// household is told that their paid year may have changed.
    ///
    /// Every service that puts an existing member in a household calls this,
    /// after it has done so, and publishes the member's own [MemberChanged]
    /// itself. A payment that already names a household stays with that
    /// household, and a payment for another year is left as it is.
    ///
    /// @param member    the member, already in the household
    /// @param household the household the member has gained
    void joined(Member member, Household household) {
        Optional<Fee> own = fees.findByMemberIdAndYear(member.getId(), currentYear());
        if (own.isEmpty() || own.get().getKind() != FeeKind.HOUSEHOLD || own.get().getHouseholdId() != null) {
            return;
        }
        own.get().coverHousehold(household.getId());
        for (Member other : members.findByHousehold(household.getId())) {
            if (!other.getId().equals(member.getId())) {
                events.publishEvent(new MemberChanged(other.getId()));
            }
        }
    }

    /// This year's status of each of some members, with the year's payments and
    /// the members' accounts read once.
    ///
    /// @param members members, whose households must be loaded or proxies
    /// @return each member's status for this year, in the order of `members`
    List<FeeStatus> thisYear(List<Member> members) {
        int year = currentYear();
        Map<Long, Fee> own = new HashMap<>();
        for (Fee fee : fees.findByYear(year)) {
            Long memberId = fee.getMemberId();
            if (memberId != null) {
                own.put(memberId, fee);
            }
        }
        Map<Long, Instant> households = new HashMap<>();
        for (HouseholdPayment payment : fees.householdPayments(year, FeeKind.HOUSEHOLD)) {
            households.merge(payment.getHouseholdId(), payment.getPaidAt(), FeeLedger::earlier);
        }
        Map<Long, String> emails = new HashMap<>();
        List<Long> ids = members.stream().map(Member::getId).toList();
        if (!ids.isEmpty()) {
            for (Account account : accounts.findByMembers(ids)) {
                emails.put(account.getMember().getId(), account.getEmail());
            }
        }
        List<FeeStatus> statuses = new ArrayList<>();
        for (Member member : members) {
            Household household = member.getHousehold();
            Instant householdPaid = household == null ? null : households.get(household.getId());
            statuses.add(status(year, own.get(member.getId()), householdPaid, emails.get(member.getId())));
        }
        return statuses;
    }

    /// @param member a member
    /// @return one status per year the member or their household has a payment
    ///         for, and this year, newest first
    List<FeeStatus> history(Member member) {
        String email = accounts.findByMember(member.getId()).map(Account::getEmail).orElse(null);
        Map<Integer, Fee> own = new HashMap<>();
        for (Fee fee : fees.findByMemberId(member.getId())) {
            own.put(fee.getYear(), fee);
        }
        Map<Integer, Instant> household = new HashMap<>();
        Household current = member.getHousehold();
        if (current != null) {
            for (HouseholdPayment payment : fees.householdPaymentsOf(current.getId(), FeeKind.HOUSEHOLD)) {
                household.merge(payment.getYear(), payment.getPaidAt(), FeeLedger::earlier);
            }
        }
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.add(currentYear());
        years.addAll(own.keySet());
        years.addAll(household.keySet());
        List<FeeStatus> statuses = new ArrayList<>();
        for (int year : years) {
            statuses.add(status(year, own.get(year), household.get(year), email));
        }
        return statuses;
    }

    private FeeStatus status(int year, @Nullable Fee own, @Nullable Instant household, @Nullable String email) {
        if (own != null) {
            return new FeeStatus(year, own.getPaidAt(), own.getKind(), false, null);
        }
        if (household != null) {
            return new FeeStatus(year, household, FeeKind.HOUSEHOLD, true, null);
        }
        PaymentInstruction payment = email == null ? null
                : new PaymentInstruction(association.bankgiro(), association.feeIndividualOre(),
                association.feeHouseholdOre(), email);
        return new FeeStatus(year, null, null, false, payment);
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}
