package se.teaterihuskvarna.member;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Objects;
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

    FeeService(FeeRepository fees, MemberRepository members, FeeLedger ledger, AssociationSettings association) {
        this.fees = fees;
        this.members = members;
        this.ledger = ledger;
        this.association = association;
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
    }
}
