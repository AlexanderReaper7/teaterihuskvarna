package se.teaterihuskvarna.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/// One member's place in one offer. The offer and the member are plain ids
/// rather than associations, as on `Administrator`: nothing here loads either
/// through the registration, and the queries join them where a name is needed.
@Entity
@Table(name = "offer_registration")
public class OfferRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false)
    private long offerId;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected OfferRegistration() {
        // for JPA
    }

    /// @param offerId  the offer registered for
    /// @param memberId the member who registered
    OfferRegistration(long offerId, long memberId) {
        this.offerId = offerId;
        this.memberId = memberId;
    }

    public Long getId() {
        return id;
    }

    public long getOfferId() {
        return offerId;
    }

    public long getMemberId() {
        return memberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
