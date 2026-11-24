package se.teaterihuskvarna.outbox;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import se.teaterihuskvarna.WithoutMessage;

/// Hears from PostgreSQL that a transaction has committed an outbox row the
/// database wrote itself, and hands the due rows to their threads at once.
///
/// [Outbox#add] tries its own rows after the commit and needs none of this.
/// A row a trigger writes, as `V17__brevo_contact_queue.sql` does for a
/// member's Brevo contact, is written with no Java code running. The trigger
/// calls `pg_notify`, and PostgreSQL delivers that when the transaction
/// commits, once however many rows it wrote, and never for one that rolls
/// back. This class is the connection that listens.
///
/// It saves a wait and nothing else: [Outbox#retry] finds the same rows
/// within a minute. So a lost connection is logged, a new one is tried after
/// a second, and a row committed in between waits for the job.
///
/// The connection is one of the pool's, kept for as long as the application
/// runs, so the pool has one fewer for everything else.
@Component
final class OutboxNotifications implements SmartLifecycle {

    /// The channel `brevo_contact_queue` in the migration notifies.
    static final String CHANNEL = "outbox";

    private static final Logger LOG = LoggerFactory.getLogger(OutboxNotifications.class);

    /// How long one read waits for a notification before it looks whether
    /// the application is stopping.
    private static final int READ_MILLIS = 1000;
    private static final Duration BEFORE_RECONNECT = Duration.ofSeconds(1);

    private final DataSource dataSource;
    private final Outbox outbox;
    private volatile boolean running;
    private @Nullable Thread thread;

    OutboxNotifications(DataSource dataSource, Outbox outbox) {
        this.dataSource = dataSource;
        this.outbox = outbox;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = Thread.ofPlatform().name("outbox-listen").daemon().start(this::listen);
    }

    /// Waits for the thread, so its connection is back in the pool before
    /// the pool closes.
    @Override
    public synchronized void stop() {
        running = false;
        Thread listening = thread;
        thread = null;
        if (listening == null) {
            return;
        }
        try {
            listening.join(Duration.ofMillis(READ_MILLIS).multipliedBy(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    // Deliberately broad: whatever goes wrong, the answer is a new connection.
    private void listen() {
        while (running) {
            try (Connection connection = dataSource.getConnection()) {
                hear(connection);
            } catch (SQLException | RuntimeException e) {
                if (running) {
                    LOG.warn("Lost the connection that listens for outbox rows; opening a new one",
                            WithoutMessage.of(e));
                    LockSupport.parkNanos(BEFORE_RECONNECT.toNanos());
                }
            }
        }
    }

    private void hear(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + CHANNEL);
        }
        try {
            PGConnection postgres = connection.unwrap(PGConnection.class);
            // Rows committed while nothing listened.
            outbox.dispatchDue();
            while (running) {
                PGNotification[] heard = postgres.getNotifications(READ_MILLIS);
                if (heard != null && heard.length > 0) {
                    outbox.dispatchDue();
                }
            }
        } finally {
            stopListening(connection);
        }
    }

    /// The connection goes back to the pool, where its next borrower must
    /// not be sent notifications. A connection that is already broken cannot
    /// be told, and the pool discards it.
    private static void stopListening(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("UNLISTEN *");
        } catch (SQLException e) {
            LOG.debug("Could not stop listening on a connection that is closing", WithoutMessage.of(e));
        }
    }
}
