package se.teaterihuskvarna.offer;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes offers. Package private, so only [OfferService] can reach
/// the table: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface OfferRepository extends JpaRepository<Offer, Long> {

    /// The offer row, locked (`SELECT ... FOR UPDATE`) until the transaction
    /// ends. [OfferService#register] takes it before counting, so two members
    /// registering for the last place count one after the other.
    ///
    /// @param id the offer's id
    /// @return the offer, locked, or empty
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> lockById(@Param("id") long id);

    /// Published offers whose registration has not closed, in the order they
    /// happen, with undated ones last. The closing rule is [Offer#closesAt].
    ///
    /// @param now the moment to ask about
    /// @return the offers a member can register for
    @Query("""
            select o from Offer o
            where o.published = true
              and (coalesce(o.registrationClosesAt, o.startsAt) is null
                   or coalesce(o.registrationClosesAt, o.startsAt) > :now)
            order by o.startsAt asc nulls last, o.id asc
            """)
    List<Offer> findOpen(@Param("now") Instant now);

    /// @return every offer, newest first
    @Query("select o from Offer o order by o.createdAt desc, o.id desc")
    List<Offer> findAllNewestFirst();
}
