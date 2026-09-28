package se.teaterihuskvarna.member;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/// Computes fee statuses, the rule [FeeStatus] states, from the `fee` table. The
/// services in this package call it inside their own transactions.
@Component
class FeeLedger {

    /// The association is in Huskvarna, so "this year" is the year there,
    /// whatever zone the container runs in. New Year's night is the only time
    /// the two can differ.
    static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    private final FeeRepository fees;
    private final AssociationSettings association;

    FeeLedger(FeeRepository fees, AssociationSettings association) {
        this.fees = fees;
        this.association = association;
    }

    /// @return the current year in Sweden
    int currentYear() {
        return Year.now(SWEDEN).getValue();
    }

    /// Reads one year's payments once, for the status of many members.
    ///
    /// @param year a year
    /// @return the statuses for that year
    Payments year(int year) {
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
        return new Payments(year, own, households);
    }

    /// @param member a member
    /// @return one status per year the member or their household has a payment
    ///         for, and this year, newest first
    List<FeeStatus> history(Member member) {
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
            statuses.add(status(year, own.get(year), household.get(year), member.getFullName()));
        }
        return statuses;
    }

    private FeeStatus status(int year, @Nullable Fee own, @Nullable Instant household, String fullName) {
        if (own != null) {
            return new FeeStatus(year, own.getPaidAt(), own.getKind(), false, null);
        }
        if (household != null) {
            return new FeeStatus(year, household, FeeKind.HOUSEHOLD, true, null);
        }
        return new FeeStatus(year, null, null, false, new PaymentInstruction(association.bankgiro(),
                association.feeIndividualOre(), association.feeHouseholdOre(), fullName));
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    /// One year's payments, read once.
    final class Payments {

        private final int value;
        private final Map<Long, Fee> own;
        private final Map<Long, Instant> households;

        private Payments(int value, Map<Long, Fee> own, Map<Long, Instant> households) {
            this.value = value;
            this.own = own;
            this.households = households;
        }

        /// @param member a member, whose household must be loaded or a proxy
        /// @return the member's status for this year
        FeeStatus status(Member member) {
            Household household = member.getHousehold();
            Instant householdPaid = household == null ? null : households.get(household.getId());
            return FeeLedger.this.status(value, own.get(member.getId()), householdPaid, member.getFullName());
        }
    }
}
