package se.teaterihuskvarna.administrator;
import java.time.Instant;
/// One line of the administrator removal log, with names instead of ids, so the
/// adapters never touch the entity([AdminRemovalLog]).
///
///  @param id                the log row
///  @param administratorName the administrator the event is about
///  @param actorName         the administrator, who did it
///  @param event             what happened, `Removed` or `restored`
///  @param occurredAt        when it happened
///  @param device            the device, that actor have used

public record AdminRemovalLogEntry(
        long id,
        String administratorName,
        String actorName,
        String event,
        Instant occurredAt,
        String device) {
}
