package se.teaterihuskvarna.member;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/// Reads and writes households. Package private for the same reason as
/// [MemberRepository]: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface HouseholdRepository extends JpaRepository<Household, Long> {

    /// @return every household by name
    @Query("select h from Household h order by lower(h.name), h.id")
    List<Household> findAllByName();
}
