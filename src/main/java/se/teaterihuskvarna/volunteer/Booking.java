package se.teaterihuskvarna.volunteer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// One member's booking of one shift. The shift and the member are plain ids
/// rather than associations; the queries join them where a name is needed.
@Entity
@Table(name = "volunteer_booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "shift_id", nullable = false)
    private long shiftId;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reminded_at")
    private @Nullable Instant remindedAt;

    protected Booking() {
        // for JPA
    }

    Booking(long shiftId, long memberId, Instant now) {
        this.shiftId = shiftId;
        this.memberId = memberId;
        this.createdAt = now;
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
        return id;
    }

    public long getShiftId() {
        return shiftId;
    }

    public long getMemberId() {
        return memberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getRemindedAt() {
        return remindedAt;
    }

    /// @param now when the reminder went out
    void reminded(Instant now) {
        this.remindedAt = now;
    }
}
