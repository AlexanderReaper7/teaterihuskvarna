package se.teaterihuskvarna.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// Something members can register for, with a limited number of places or none.
///
/// Column lengths mirror `V7__offers.sql` by hand, because `ddl-auto: validate`
/// does not compare them: `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "offer")
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = OfferForm.DESCRIPTION_MAX)
    private String description;

    @Column(name = "starts_at")
    private @Nullable Instant startsAt;

    @Column(name = "registration_closes_at")
    private @Nullable Instant registrationClosesAt;

    @Column(name = "capacity")
    private @Nullable Integer capacity;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;

    protected Offer() {
        // for JPA
    }

    /// A new offer starts unpublished, so members see nothing until an
    /// administrator publishes it.
    ///
    /// @param values the title, description, times and capacity
    Offer(OfferValues values) {
        this.title = values.title();
        this.description = values.description();
        this.startsAt = values.startsAt();
        this.registrationClosesAt = values.registrationClosesAt();
        this.capacity = values.capacity();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public @Nullable Instant getStartsAt() {
        return startsAt;
    }

    public @Nullable Instant getRegistrationClosesAt() {
        return registrationClosesAt;
    }

    public @Nullable Integer getCapacity() {
        return capacity;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /// @param values the new title, description, times and capacity
    void change(OfferValues values) {
        this.title = values.title();
        this.description = values.description();
        this.startsAt = values.startsAt();
        this.registrationClosesAt = values.registrationClosesAt();
        this.capacity = values.capacity();
        this.updatedAt = Instant.now();
    }

    /// @param published whether members can see the offer
    void setPublished(boolean published) {
        this.published = published;
        this.updatedAt = Instant.now();
    }

    /// Registration closes at `registration_closes_at`, or when the offer
    /// starts if no closing time is set. With neither it never closes.
    ///
    /// @return when members can no longer register or cancel, or null for never
    public @Nullable Instant closesAt() {
        return registrationClosesAt != null ? registrationClosesAt : startsAt;
    }

    /// @param now the moment to ask about
    /// @return whether members can register and cancel at that moment
    public boolean isOpenAt(Instant now) {
        Instant closes = closesAt();
        return closes == null || now.isBefore(closes);
    }

    /// Never below zero: an administrator may lower the capacity below the
    /// number already registered, and those registrations stay.
    ///
    /// @param registered how many members are registered
    /// @return the places left, or null when the offer has no limit
    public @Nullable Integer placesLeft(long registered) {
        if (capacity == null) {
            return null;
        }
        return (int) Math.max(0, capacity - registered);
    }
}
