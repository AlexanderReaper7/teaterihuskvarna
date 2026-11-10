package se.teaterihuskvarna.outbox;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import se.teaterihuskvarna.WithoutMessage;

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
/// One exception, which an agent built after the review on 2026-11-10 and the
/// user decided the same day: work stored with a `not_after`, which is mail
/// whose link or code expires or whose text names a day. It is retried the same way
/// until that moment. After it the row is deleted undone and logged with its
/// id and kind, by the attempt that finds it or by [#retry] within a minute,
/// and it is never handed to its handler. Without this a mail that kept
/// failing kept its login link in the table in plain text, and went out after
/// the link had expired. Work without a `not_after` follows the user's rule.
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
    /// tries it after the commit, and again until it is done.
    ///
    /// @param kind    the [OutboxHandler#kind] that does it
    /// @param payload what the handler needs
    /// @throws IllegalArgumentException if no handler does that kind
    public void add(String kind, String payload) {
        store(kind, payload, null);
    }

    /// Stores work that is wrong to do after a moment, such as a mail whose
    /// link expires then. It is tried like other work until that moment, and
    /// deleted undone after it.
    ///
    /// @param kind     the [OutboxHandler#kind] that does it
    /// @param payload  what the handler needs
    /// @param notAfter the last moment the work may be done
    /// @throws IllegalArgumentException if no handler does that kind
    public void add(String kind, String payload, Instant notAfter) {
        store(kind, payload, notAfter);
    }

    private void store(String kind, String payload, @Nullable Instant notAfter) {
        if (!handlers().containsKey(kind)) {
            throw new IllegalArgumentException("no outbox handler for " + kind);
        }
        // The cast gives the parameter a type when it is null.
        long id = jdbc.sql("""
                INSERT INTO outbox (kind, payload, not_after) VALUES (?, ?, CAST(? AS TIMESTAMPTZ))
                RETURNING id""")
                .param(kind)
                .param(payload)
                .param(notAfter == null ? null : OffsetDateTime.ofInstant(notAfter, ZoneOffset.UTC))
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

    /// Deletes every row past its `not_after`, then hands every due row to its
    /// kind's thread, the longest waiting first, up to a batch per kind, so
    /// rows of one kind stuck behind a slow Brevo never crowd a retried mail
    /// out of the batch. The scheduler's thread
    /// only reads ids, so neither the mail nor Spring's other scheduled jobs
    /// wait on Brevo. A row already waiting for its thread is not handed over
    /// twice.
    @Scheduled(fixedDelayString = "PT1M")
    public void retry() {
        deleteExpired();
        List<Entry> due = jdbc.sql("""
                SELECT id, kind FROM (
                    SELECT id, kind, next_attempt_at,
                        row_number() OVER (PARTITION BY kind ORDER BY next_attempt_at, id) AS place
                    FROM outbox WHERE next_attempt_at <= now()) AS due
                WHERE place <= ?
                ORDER BY next_attempt_at, id""")
                .param(BATCH)
                .query(Entry.class)
                .list().stream().map(Objects::requireNonNull).toList();
        for (Entry row : due) {
            dispatch(row.kind(), row.id());
        }
    }

    /// A row may wait six hours for its next attempt, so its `not_after` can
    /// pass long before anything would try it. This takes it then, payload
    /// and all. A row an attempt holds is skipped, so the scheduler's thread
    /// never waits on the other system, and is taken on the next run.
    private void deleteExpired() {
        List<Entry> expired = jdbc.sql("""
                DELETE FROM outbox WHERE id IN (
                    SELECT id FROM outbox WHERE not_after < now() FOR UPDATE SKIP LOCKED)
                RETURNING id, kind""")
                .query(Entry.class)
                .list().stream().map(Objects::requireNonNull).toList();
        for (Entry row : expired) {
            expired(row.id(), row.kind());
        }
    }

    /// The id and the kind are all the log gets: the payload names a person.
    private static void expired(long id, String kind) {
        LOG.warn("Outbox row {} of kind {} passed its not_after undone and was deleted", id, kind);
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
                    SELECT kind, payload, attempts, COALESCE(not_after < now(), FALSE) AS expired FROM outbox
                    WHERE id = ? AND next_attempt_at <= now()
                    FOR UPDATE SKIP LOCKED""")
                    .param(id)
                    .query(Row.class)
                    .optional();
            if (found.isEmpty()) {
                return;
            }
            Row row = found.get();
            if (row.expired()) {
                jdbc.sql("DELETE FROM outbox WHERE id = ?").param(id).update();
                expired(id, row.kind());
                return;
            }
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

    /// The log gets the row's id, kind and attempt, and what failed as
    /// [WithoutMessage] gives it: class names and stack frames. A handler's
    /// exception is never logged as it is, because its message can name a
    /// person, as a relay's refusal names the recipient. The frames stay
    /// because, with the message gone, they are what tells a handler's own
    /// fault from the other system's.
    private void failed(long id, Row row, RuntimeException e) {
        int attempts = row.attempts() + 1;
        if (attempts == MAX_ATTEMPTS) {
            LOG.error("Outbox row {} of kind {} has failed {} times; still trying every {}", id, row.kind(),
                    attempts, LONGEST_WAIT, WithoutMessage.of(e));
        } else {
            LOG.warn("Outbox row {} of kind {} failed, attempt {}", id, row.kind(), attempts, WithoutMessage.of(e));
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

    private record Row(String kind, String payload, int attempts, boolean expired) {
    }

    private record Entry(long id, String kind) {
    }
}
