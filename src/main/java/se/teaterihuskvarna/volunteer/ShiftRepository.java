package se.teaterihuskvarna.volunteer;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes shifts. Package private, so only [ShiftService] can reach
/// the table: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface ShiftRepository extends JpaRepository<Shift, Long> {

    /// The shift row, locked until the transaction ends, so two members booking
    /// the last place count one after the other.
    ///
    /// @param id the shift
    /// @return the shift, locked, or empty
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shift s where s.id = :id")
    Optional<Shift> lockById(@Param("id") long id);

    /// @param from the earliest start
    /// @return the shifts starting at or after it, earliest first, cloakroom before serving
    @Query("select s from Shift s where s.startsAt >= :from order by s.startsAt, s.task, s.id")
    List<Shift> findStartingFrom(@Param("from") Instant from);

    /// @param now the moment
    /// @return the shifts that have started and whose start is not yet reported
    @Query("select s from Shift s where s.startsAt <= :now and s.startReported = false order by s.id")
    List<Shift> findStartedUnreported(@Param("now") Instant now);

    /// @param before the end of the range, not included
    /// @return the shifts starting before it, latest first
    @Query("select s from Shift s where s.startsAt < :before order by s.startsAt desc, s.task, s.id")
    List<Shift> findStartingBefore(@Param("before") Instant before);
}
