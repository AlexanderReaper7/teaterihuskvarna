package se.teaterihuskvarna.administrator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.time.Instant;
import jakarta.persistence.Id;
/// The rows in history of administrator removals. Rows are only added,
/// never changed, because `Administartor.recative()` clears `removeAt` and
/// `removeBy` and would otherwise erase who removed whom.
///
///Workflow: The file name will take next available V<n>__...sql,
/// and the already runible Flyways files will never be changed.
/// Eny changes on flyways exiting files will break all flyways check work,
///  and the application will fail
///
/// For JPA only. Hibernate needs a no-argument constructor to create the object
/// when it reads a row, and the specification allows `protected`. It is not
/// `public`, so application code not create a log row without an
/// administrator, an actor and an event.

@Entity
@Table(name="administrator_removal_log")
public class AdminRemovalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "administrator_id", nullable = false)
    private Long administratorId;

    @Column(name="actor_id", nullable = false)
    private Long actorId;

    @Column(name ="event", nullable = false, length = 30)
    private String event;

    @Column(name="occurred_at", nullable = false)
    private Instant occurredAt= Instant.now();

    protected AdminRemovalLog() {
        //for JPA
    }
/// @param administratorId the administrator the event is about
/// @param actorId         the administrator who did it
/// @param event           what happened, at most 30 characters
    public AdminRemovalLog(Long administratorId, Long actorId, String event) {
        this.administratorId = administratorId;
        this.actorId = actorId;
        this.event = event;
    }

    public long getId() {
        return id;
    }

    public Long getAdministratorId() {
        return administratorId;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getEvent() {
        return event;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
