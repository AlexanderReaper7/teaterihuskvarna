package se.teaterihuskvarna.volunteer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.teaterihuskvarna.member.Recipient;

/// Reads and writes bookings. Package private, like [ShiftRepository].
///
/// The queries that need a name join `Member` and `Account` from the member
/// package by entity name. That package's repositories stay private to it.
interface BookingRepository extends JpaRepository<Booking, Long> {

    /// @param shiftId the shift
    /// @return how many members booked it
    long countByShiftId(long shiftId);

    /// @param shiftId  the shift
    /// @param memberId the member
    /// @return whether the member booked the shift
    boolean existsByShiftIdAndMemberId(long shiftId, long memberId);

    /// @param shiftId  the shift
    /// @param memberId the member
    /// @return how many rows were deleted, 0 or 1
    @Modifying
    @Query("delete from Booking b where b.shiftId = :shiftId and b.memberId = :memberId")
    int deleteBooking(@Param("shiftId") long shiftId, @Param("memberId") long memberId);

    /// @param shiftIds the shifts to count
    /// @return one row per shift with at least one booking
    @Query("""
            select new se.teaterihuskvarna.volunteer.BookingCount(b.shiftId, count(b))
            from Booking b
            where b.shiftId in :shiftIds
            group by b.shiftId
            """)
    List<BookingCount> countByShiftIds(@Param("shiftIds") Collection<Long> shiftIds);

    /// @param memberId the member
    /// @return the ids of every shift the member booked
    @Query("select b.shiftId from Booking b where b.memberId = :memberId")
    List<Long> findShiftIdsByMemberId(@Param("memberId") long memberId);

    /// @param shiftId the shift
    /// @return who booked it, in the order they did
    @Query("""
            select new se.teaterihuskvarna.volunteer.Volunteer(
                m.id, m.fullName, a.email, m.contact.phone, b.createdAt)
            from Booking b
            join Member m on m.id = b.memberId
            left join Account a on a.member = m
            where b.shiftId = :shiftId
            order by b.createdAt, b.id
            """)
    List<Volunteer> findVolunteers(@Param("shiftId") long shiftId);

    /// @param since the earliest shift start that counts
    /// @param until the end of the range, not included, so a booking for a shift still to come does not count
    /// @return the members with an account who booked a shift starting in the range, by name
    @Query("""
            select distinct new se.teaterihuskvarna.member.Recipient(m.id, m.fullName, a.email)
            from Booking b
            join Shift s on s.id = b.shiftId
            join Member m on m.id = b.memberId
            join Account a on a.member = m
            where s.startsAt >= :since and s.startsAt < :until
            order by m.fullName, m.id
            """)
    List<Recipient> findRecipientsBetween(@Param("since") Instant since, @Param("until") Instant until);

    /// The bookings that are owed a reminder: for a shift tomorrow, or for a
    /// shift later today booked before today began.
    ///
    /// @param now             shifts that have started are skipped
    /// @param startOfToday    a booking for today made after this gets no reminder
    /// @param startOfTomorrow where tomorrow begins
    /// @param endOfTomorrow   the end of the window
    /// @return the bookings with no reminder sent yet
    @Query("""
            select b from Booking b
            join Shift s on s.id = b.shiftId
            where s.startsAt > :now and s.startsAt < :endOfTomorrow and b.remindedAt is null
              and (s.startsAt >= :startOfTomorrow or b.createdAt < :startOfToday)
            order by b.id
            """)
    List<Booking> findUnreminded(@Param("now") Instant now, @Param("startOfToday") Instant startOfToday,
            @Param("startOfTomorrow") Instant startOfTomorrow, @Param("endOfTomorrow") Instant endOfTomorrow);

    /// @param memberId the member
    /// @return the member's account address, or empty for a member without one
    @Query("select a.email from Account a where a.member.id = :memberId")
    List<String> findEmail(@Param("memberId") long memberId);
}
