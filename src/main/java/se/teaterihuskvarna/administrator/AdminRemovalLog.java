package se.teaterihuskvarna.administrator;

import jakarta.persistence.*;
import java.time.Instant;

/// The rows in history of administrator removals. Rows are only added,
/// never changed, because `Administartor.recative()` clears `removeAt` and
/// `removeBy` and would otherwise erase who removed whom.
///
///Workflow: The file name will take next available V<n>__...sql,
/// and the already runible Flyways files will never be changed.
/// Eny changes on flyways exiting files will break all flyways check work,
///  and the application will fail


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

    @Override
    public String toString() {
        return "AdminRemovalLog{" +
                "id=" + id +
                ", administratorId=" + administratorId +
                ", actorId=" + actorId +
                ", event='" + event + '\'' +
                ", occurredAt='" + occurredAt + '\'' +
                '}';
    }
}
