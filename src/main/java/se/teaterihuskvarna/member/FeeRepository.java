package se.teaterihuskvarna.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes fee payments. Package private for the same reason as
/// [MemberRepository]: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface FeeRepository extends JpaRepository<Fee, Long> {

    /// @param year a year
    /// @return every payment for it, anonymised ones included
    List<Fee> findByYear(int year);

    /// @param memberId a member
    /// @return the member's own payments, every year
    List<Fee> findByMemberId(Long memberId);

    /// @param memberId a member
    /// @param year     a year
    /// @return the member's own payment for that year, or empty
    Optional<Fee> findByMemberIdAndYear(Long memberId, int year);

    /// Household payments for a year, each with the household it was paid for.
    /// The household stays covered after the payer moves out or is deleted,
    /// and a payer in no household covers nobody but themselves.
    ///
    /// @param year a year
    /// @param kind always [FeeKind#HOUSEHOLD]; a parameter rather than a literal in the query
    /// @return one row per household payment that names a household
    @Query("""
            select f.householdId as householdId, f.year as year, f.paidAt as paidAt
            from Fee f
            where f.year = :year and f.kind = :kind and f.householdId is not null""")
    List<HouseholdPayment> householdPayments(@Param("year") int year, @Param("kind") FeeKind kind);

    /// The same as [#householdPayments], for one household and every year.
    ///
    /// @param householdId a household
    /// @param kind        always [FeeKind#HOUSEHOLD]
    /// @return one row per household payment made for the household
    @Query("""
            select f.householdId as householdId, f.year as year, f.paidAt as paidAt
            from Fee f
            where f.householdId = :householdId and f.kind = :kind""")
    List<HouseholdPayment> householdPaymentsOf(@Param("householdId") Long householdId, @Param("kind") FeeKind kind);
}
