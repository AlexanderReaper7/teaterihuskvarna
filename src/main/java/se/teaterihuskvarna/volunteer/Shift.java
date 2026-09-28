package se.teaterihuskvarna.volunteer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/// One volunteer shift at one event.
///
/// Column lengths mirror `V9__volunteer_shifts.sql` by hand, because
/// `ddl-auto: validate` does not compare them:
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "volunteer_shift")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "event_title", nullable = false, length = 200)
    private String eventTitle;

    @Column(name = "event_slug", nullable = false, length = 200)
    private String eventSlug;

    @Enumerated(EnumType.STRING)
    @Column(name = "task", nullable = false, length = 20)
    private Task task;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "places", nullable = false)
    private int places;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Shift() {
        // for JPA
    }

    Shift(String eventId, String eventTitle, String eventSlug, Task task, Instant startsAt, Instant endsAt,
            int places, Instant now) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.eventSlug = eventSlug;
        this.task = task;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.places = places;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public String getEventSlug() {
        return eventSlug;
    }

    public Task getTask() {
        return task;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getPlaces() {
        return places;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /// @param booked how many members have booked it
    /// @return the places left, never below zero
    int placesLeft(long booked) {
        return (int) Math.max(0, places - booked);
    }
}
