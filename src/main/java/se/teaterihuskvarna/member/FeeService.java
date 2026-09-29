package se.teaterihuskvarna.member;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/// An administrator marks this year's fee paid, and undoes the mark (R019).
/// Nobody pays through the site: the fee goes to the bankgiro, and an
/// administrator records it here after seeing it arrive.
@Service
@Validated
@Transactional(readOnly = true)
public class FeeService {

    private final FeeRepository fees;
    private final MemberRepository members;
    private final FeeLedger ledger;
    private final AssociationSettings association;
    private final ApplicationEventPublisher events;

    FeeService(FeeRepository fees, MemberRepository members, FeeLedger ledger, AssociationSettings association,
            ApplicationEventPublisher events) {
        this.fees = fees;
        this.members = members;
        this.ledger = ledger;
        this.association = association;
        this.events = events;
    }

    /// For the member's Brevo contact: the latest year their fee is paid,
    /// by themselves or their household.
    ///
    /// @param memberId a member
    /// @return the year, or null if no year is paid or there is no such member
    public @Nullable Integer latestPaidYear(long memberId) {
        Optional<Member> member = members.findById(memberId);
        if (member.isEmpty()) {
            return null;
        }
        for (FeeStatus status : ledger.history(member.get())) {
            if (status.paidAt() != null) {
                return status.year();
            }
        }
        return null;
    }

    /// Records a payment for this year, the year taken in Sweden. A
    /// [FeeKind#HOUSEHOLD] payment covers everyone in the member's household,
    /// and keeps covering it if the member later moves or is deleted.
    ///
    /// @param memberId        the member who paid
    /// @param mark            the kind, and the amount if it differs from the configured one
    /// @param administratorId the administrator marking it
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    /// @throws NoSuchMember if there is no such member
    /// @throws FeeAlreadyMarked if the member already has a payment of their own this year
    @Transactional
    public void markPaid(long memberId, @Valid FeeMark mark, long administratorId) {
        Member member = members.findById(memberId).orElseThrow(NoSuchMember::new);
        int year = ledger.currentYear();
        if (fees.findByMemberIdAndYear(memberId, year).isPresent()) {
            throw new FeeAlreadyMarked();
        }
        FeeKind kind = Objects.requireNonNull(mark.kind());
        Integer given = mark.amountOre();
        int amount = given == null ? association.feeOre(kind) : given;
        try {
            Household household = member.getHousehold();
            Long householdId = kind == FeeKind.HOUSEHOLD && household != null ? household.getId() : null;
            fees.saveAndFlush(new Fee(memberId, year, kind, amount, Instant.now(), administratorId, householdId));
            covered(memberId, householdId);
        } catch (DataIntegrityViolationException e) {
            // Two marks at once: fee_member_id_year_key refused the second.
            throw new FeeAlreadyMarked();
        }
    }

    /// Removes the member's own payment for this year, such as one marked on
    /// the wrong member. A household payment is undone on the member who paid it.
    ///
    /// @param memberId the member whose mark to undo
    /// @throws NoSuchFee if the member has no payment of their own this year
    @Transactional
    public void undo(long memberId) {
        Fee fee = fees.findByMemberIdAndYear(memberId, ledger.currentYear()).orElseThrow(NoSuchFee::new);
        fees.delete(fee);
        covered(memberId, fee.getHouseholdId());
    }

    /// Tells the payer's contact, and each contact in the household a
    /// household fee covers, that the paid year may have changed.
    private void covered(long memberId, @Nullable Long householdId) {
        events.publishEvent(new MemberChanged(memberId));
        if (householdId != null) {
            for (Member member : members.findByHousehold(householdId)) {
                if (member.getId() != memberId) {
                    events.publishEvent(new MemberChanged(member.getId()));
                }
            }
        }
    }
}
