package se.teaterihuskvarna.outbox;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/// Work for another system, stored in the transaction that causes it and done
/// after the commit. `docs/decisions/0026-outbox-and-brevo-contacts.md`.
///
/// [#add] writes a row in the caller's transaction. After the commit the row
/// is tried at once on a thread of the outbox's own, so the caller never waits
/// for the other system. One thread per kind of work: an attempt holds a
/// database connection while it waits on the other system, so a burst of
/// changes ties up one connection per kind, plus one for the retry job, and a
/// slow Brevo never holds back a login mail. A failed attempt stays in the table, and
/// [#retry] hands it to its thread again, waiting twice as long after each
/// failure, one minute at first and six hours at most. A row is never given up: the user
/// decided that mail is retried until it is sent. After [#MAX_ATTEMPTS] it is
/// logged as an error once, so someone looks, and tried every six hours.
///
/// An attempt locks its row with `SKIP LOCKED`, so the immediate attempt and
/// the job never do the same row at once, and neither do two instances.
@Component
public final class Outbox {

    /// How many failures a row has when it is logged as an error.
    static final int MAX_ATTEMPTS = 10;

    private static final Logger LOG = LoggerFactory.getLogger(Outbox.class);
    private static final Duration FIRST_WAIT = Duration.ofMinutes(1);
    private static final Duration LONGEST_WAIT = Duration.ofHours(6);
    private static final int BATCH = 50;

    /// Rows waiting for the thread. Past this a row waits for [#retry].
    private static final int QUEUE = 1000;

    private final JdbcClient jdbc;
    private final ObjectProvider<OutboxHandler> found;
    private final Map<String, ThreadPoolTaskExecutor> workers = new ConcurrentHashMap<>();
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();
    private final TransactionTemplate transactions;
    private volatile @Nullable Map<String, OutboxHandler> handlers;

    /// The handlers are looked up on first use rather than taken here, since
    /// a handler may need a service that sends mail, and so this class.
    Outbox(JdbcClient jdbc, ObjectProvider<OutboxHandler> handlers, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.found = handlers;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /// Stores work in the current transaction, or at once outside one, and
    /// tries it after the commit.
    ///
    /// @param kind    the [OutboxHandler#kind] that does it
    /// @param payload what the handler needs
    /// @throws IllegalArgumentException if no handler does that kind
    public void add(String kind, String payload) {
        if (!handlers().containsKey(kind)) {
            throw new IllegalArgumentException("no outbox handler for " + kind);
        }
        long id = jdbc.sql("INSERT INTO outbox (kind, payload) VALUES (?, ?) RETURNING id")
                .param(kind)
                .param(payload)
                .query(Long.class)
                .single();
        AfterCommit.run(() -> dispatch(kind, id));
    }

    /// Lets a running attempt finish when the application stops. A row still
    /// queued stays in the table for the next start.
    @PreDestroy
    void stop() {
        workers.values().forEach(ThreadPoolTaskExecutor::shutdown);
    }

    /// Hands every due row to its kind's thread, the longest waiting first,
    /// up to a batch per kind, so rows of one kind stuck behind a slow Brevo
    /// never crowd a retried mail out of the batch. The scheduler's thread
    /// only reads ids, so neither the mail nor Spring's other scheduled jobs
    /// wait on Brevo. A row already waiting for its thread is not handed over
    /// twice.
    @Scheduled(fixedDelayString = "PT1M")
    public void retry() {
        List<Due> due = jdbc.sql("""
                SELECT id, kind FROM (
                    SELECT id, kind, next_attempt_at,
                        row_number() OVER (PARTITION BY kind ORDER BY next_attempt_at, id) AS place
                    FROM outbox WHERE next_attempt_at <= now()) AS due
                WHERE place <= ?
                ORDER BY next_attempt_at, id""")
                .param(BATCH)
                .query(Due.class)
                .list();
        for (Due row : due) {
            dispatch(row.kind(), row.id());
        }
    }

    /// A full queue leaves the row for [#retry].
    private void dispatch(String kind, long id) {
        if (!queued.add(id)) {
            return;
        }
        try {
            workers.computeIfAbsent(kind, Outbox::worker).execute(() -> {
                try {
                    attempt(id);
                } finally {
                    queued.remove(id);
                }
            });
        } catch (RejectedExecutionException e) {
            queued.remove(id);
            LOG.warn("Could not queue outbox row {}; the retry job will take it", id);
        }
    }

    private static ThreadPoolTaskExecutor worker(String kind) {
        ThreadPoolTaskExecutor worker = new ThreadPoolTaskExecutor();
        worker.setCorePoolSize(1);
        worker.setMaxPoolSize(1);
        worker.setQueueCapacity(QUEUE);
        worker.setThreadNamePrefix("outbox-" + kind + "-");
        worker.setWaitForTasksToCompleteOnShutdown(true);
        worker.setAwaitTerminationSeconds(10);
        worker.initialize();
        return worker;
    }

    // Deliberately broad: whatever a handler throws is a failed attempt.
    private void attempt(long id) {
        transactions.executeWithoutResult(status -> {
            Optional<Row> found = jdbc.sql("""
                    SELECT kind, payload, attempts FROM outbox
                    WHERE id = ? AND next_attempt_at <= now()
                    FOR UPDATE SKIP LOCKED""")
                    .param(id)
                    .query(Row.class)
                    .optional();
            if (found.isEmpty()) {
                return;
            }
            Row row = found.get();
            try {
                OutboxHandler handler = handlers().get(row.kind());
                if (handler == null) {
                    throw new IllegalStateException("no outbox handler for " + row.kind());
                }
                handler.handle(row.payload());
                jdbc.sql("DELETE FROM outbox WHERE id = ?").param(id).update();
            } catch (RuntimeException e) {
                failed(id, row, e);
            }
        });
    }

    private void failed(long id, Row row, RuntimeException e) {
        int attempts = row.attempts() + 1;
        if (attempts == MAX_ATTEMPTS) {
            LOG.error("Outbox row {} of kind {} has failed {} times; still trying every {}", id, row.kind(),
                    attempts, LONGEST_WAIT, e);
        } else {
            LOG.warn("Outbox row {} of kind {} failed, attempt {}", id, row.kind(), attempts, e);
        }
        jdbc.sql("""
                UPDATE outbox SET attempts = ?, next_attempt_at = now() + make_interval(secs => ?), last_error = ?
                WHERE id = ?""")
                .param(attempts)
                .param(wait(attempts).toSeconds())
                .param(e.getClass().getName())
                .param(id)
                .update();
    }

    private Map<String, OutboxHandler> handlers() {
        Map<String, OutboxHandler> known = handlers;
        if (known == null) {
            Map<String, OutboxHandler> byKind = new HashMap<>();
            found.orderedStream().forEach(handler -> {
                if (byKind.put(handler.kind(), handler) != null) {
                    throw new IllegalStateException("two outbox handlers for " + handler.kind());
                }
            });
            known = Map.copyOf(byKind);
            handlers = known;
        }
        return known;
    }

    /// @param attempts how many attempts have failed, one or more
    /// @return how long to wait before the next
    static Duration wait(int attempts) {
        Duration wait = FIRST_WAIT.multipliedBy(1L << Math.min(attempts - 1, 20));
        return wait.compareTo(LONGEST_WAIT) > 0 ? LONGEST_WAIT : wait;
    }

    private record Row(String kind, String payload, int attempts) {
    }

    private record Due(long id, String kind) {
    }
}
