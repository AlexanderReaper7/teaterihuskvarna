package se.teaterihuskvarna.member;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes members. Spring Data derives the implementation from the
/// method names, which is why `suppressions.xml` exempts this file from
/// `MethodName`: a derived query may carry underscores.
///
/// Package private on purpose. Only the application services in this package
/// can call it, so an adapter cannot reach the database without going through a
/// capability somebody named: `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// The address is on the [Account], so a search by address joins it.
interface MemberRepository extends JpaRepository<Member, Long> {

    /// Serialises household creation for one member, so two submissions cannot
    /// create separate households and leave one empty.
    ///
    /// @param memberId the member to lock
    /// @return the member, or empty when removed
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :memberId")
    Optional<Member> findForUpdate(@Param("memberId") long memberId);

    /// Members whose name, account address, phone or city contains the pattern,
    /// by name. The caller lower-cases the pattern and escapes `%`, `_` and `\`
    /// in what the person typed.
    ///
    /// @param pattern a `like` pattern in lower case, such as `%berg%`
    /// @param limit   how many rows to read at most
    /// @return the matching members with their households loaded
    @Query("""
            select m from Member m left join fetch m.household left join Account a on a.member = m
            where lower(m.fullName) like :pattern escape '\\'
               or lower(a.email) like :pattern escape '\\'
               or lower(m.contact.phone) like :pattern escape '\\'
               or lower(m.contact.city) like :pattern escape '\\'
            order by lower(m.fullName), m.id""")
    List<Member> search(@Param("pattern") String pattern, Limit limit);

    /// @return every member by name, with their households loaded
    @Query("select m from Member m left join fetch m.household order by lower(m.fullName), m.id")
    List<Member> findAllByName();

    /// @param householdId a household
    /// @return the household's members by name
    @Query("select m from Member m where m.household.id = :householdId order by lower(m.fullName), m.id")
    List<Member> findByHousehold(@Param("householdId") Long householdId);
}
