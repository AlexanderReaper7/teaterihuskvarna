package se.teaterihuskvarna.offer;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.teaterihuskvarna.member.Recipient;

/// Reads and writes registrations. Package private, like [OfferRepository].
///
/// The queries that need a name join `Member` and `Account` from the member
/// package by entity name. That package's repositories stay private to it;
/// only the mapping is shared.
interface OfferRegistrationRepository extends JpaRepository<OfferRegistration, Long> {

    /// @param offerId the offer
    /// @return how many members are registered for it
    long countByOfferId(long offerId);

    /// @param offerId  the offer
    /// @param memberId the member
    /// @return whether the member is registered for the offer
    boolean existsByOfferIdAndMemberId(long offerId, long memberId);

    /// @param offerId  the offer
    /// @param memberId the member
    /// @return how many rows were deleted, 0 or 1
    @Modifying
    @Query("delete from OfferRegistration r where r.offerId = :offerId and r.memberId = :memberId")
    int deleteRegistration(@Param("offerId") long offerId, @Param("memberId") long memberId);

    /// @param offerIds the offers to count
    /// @return one row per offer with at least one registration
    @Query("""
            select new se.teaterihuskvarna.offer.RegistrationCount(r.offerId, count(r))
            from OfferRegistration r
            where r.offerId in :offerIds
            group by r.offerId
            """)
    List<RegistrationCount> countByOfferIds(@Param("offerIds") Collection<Long> offerIds);

    /// @param memberId the member
    /// @return the ids of every offer the member is registered for
    @Query("select r.offerId from OfferRegistration r where r.memberId = :memberId")
    List<Long> findOfferIdsByMemberId(@Param("memberId") long memberId);

    /// @param offerId the offer
    /// @return who registered, in the order they did
    @Query("""
            select new se.teaterihuskvarna.offer.Registrant(
                m.id, m.fullName, a.email, m.contact.phone, r.createdAt)
            from OfferRegistration r
            join Member m on m.id = r.memberId
            left join Account a on a.member = m
            where r.offerId = :offerId
            order by r.createdAt, r.id
            """)
    List<Registrant> findRegistrants(@Param("offerId") long offerId);

    /// @param offerId the offer
    /// @return the registered members who have an account, by name
    @Query("""
            select new se.teaterihuskvarna.member.Recipient(m.id, m.fullName, a.email)
            from OfferRegistration r
            join Member m on m.id = r.memberId
            join Account a on a.member = m
            where r.offerId = :offerId
            order by m.fullName, m.id
            """)
    List<Recipient> findRecipients(@Param("offerId") long offerId);
}
