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

    @Column(name="administrator_name", nullable = false, length = 100)
    private String administratorName;

    @Column(name="actor_name", nullable = false, length = 100)
    private String actorName;

    @Column(name="device", nullable = false, length = 100)
    private String device;

    protected AdminRemovalLog() {
        //for JPA
    }
/// @param administratorId the administrator the event is about
/// @param administratorName the administrator the event is about
/// @param actorId         the administrator who did it
/// @param actorName       the administrator who did it
/// @param event           what happened, at most 30 characters
/// @param device          the device used
    public AdminRemovalLog(Long administratorId, String administratorName, Long actorId, String actorName,
         String event, String device) {
        this.administratorId = administratorId;
        this.administratorName = administratorName;
        this.actorId = actorId;
        this.actorName = actorName;
        this.event = event;
        this.device = device;
    }

    public long getId() {
        return id;
    }

    public Long getAdministratorId() {
        return administratorId;
    }

    public String getAdministratorName() {
        return administratorName;
    }

    public Long getActorId() {
        return actorId;
    }
    public String getActorName() {
        return actorName;
    }

    public String getEvent() {
        return event;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
    public String getDevice() {
        return device;
    }
}
