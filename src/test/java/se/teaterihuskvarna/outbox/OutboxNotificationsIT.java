package se.teaterihuskvarna.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import se.teaterihuskvarna.IntegrationTestSupport;

/// [OutboxNotifications] keeps listening when PostgreSQL closes its
/// connection, as a restart of the database does.
///
/// That a notification reaches the outbox at all is proved by every test of a
/// Brevo contact: no Java code tries a row the database wrote, so without the
/// listener `BrevoContactsIT` and `BrevoContactQueueIT` wait in vain.
class OutboxNotificationsIT extends IntegrationTestSupport {

    @Test
    void aLostConnectionIsReplacedByOneThatListens() {
        await(() -> listening().size() == 1, "one connection that listens");
        int lost = listening().getFirst();

        jdbc.sql("SELECT pg_terminate_backend(?)").param(lost).query(Boolean.class).single();
        await(() -> listening().size() == 1 && listening().getFirst() != lost, "a new connection that listens");

        // The trigger on account queues the member's Brevo contact and notifies.
        insertAccount("Karin Holm", "karin@example.test");

        await(() -> rowsIn("outbox") == 0, "the row the database wrote to be done");
    }

    /// The listener sends nothing after its `LISTEN`, so that stays the
    /// latest statement PostgreSQL shows for its connection.
    private List<Integer> listening() {
        return jdbc.sql("""
                        SELECT pid FROM pg_stat_activity
                        WHERE datname = current_database() AND query = 'LISTEN outbox'""")
                .query(Integer.class)
                .list().stream().map(Objects::requireNonNull).toList();
    }

    private static void await(BooleanSupplier done, String what) {
        long deadline = System.currentTimeMillis() + MAIL_WAIT_MILLIS;
        while (!done.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("waiting for %s", what).isLessThan(deadline);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}
