package se.teaterihuskvarna.outbox;

/// Does one kind of [Outbox] work. A Spring bean implementing this is found by
/// its [#kind].
public interface OutboxHandler {

    /// @return the name rows of this kind carry in `outbox.kind`, at most 40 characters
    String kind();

    /// Does the work. Throwing leaves the row for a later attempt, so the work
    /// must be safe to repeat: an attempt can succeed at the other end and
    /// still fail here, such as on a timeout.
    ///
    /// @param payload what [Outbox#add] stored
    void handle(String payload);
}
