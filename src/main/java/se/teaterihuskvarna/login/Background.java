package se.teaterihuskvarna.login;

import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/// Runs work that depends on whether an address is known, off the request
/// thread, in a transaction of its own.
///
/// [Mailer] already keeps the SMTP call off the request thread, but that is not
/// enough. Before a mail exists, a known address costs a token insert, or an
/// application upsert, that an unknown address does not, and a few milliseconds
/// of database work are measurable over enough requests. So the request thread
/// does only what every request does, validation and the rate limit, and hands
/// the rest to this class.
///
/// A failure is logged and dropped, for the same reason [Mailer] drops one: an
/// error page for a known address alone would give the address away.
@Component
public class Background {

    private static final Logger LOG = LoggerFactory.getLogger(Background.class);

    private final TaskExecutor executor;
    private final TransactionTemplate transactions;

    Background(@Qualifier("applicationTaskExecutor") TaskExecutor executor, PlatformTransactionManager manager) {
        this.executor = executor;
        this.transactions = new TransactionTemplate(manager);
    }

    /// Queues work. Inside a transaction it waits for the commit, so the work
    /// sees what the caller wrote, and nothing runs for a request that failed.
    ///
    /// @param work what to run, in a new transaction on the executor
    public void run(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(work);
                }
            });
        } else {
            dispatch(work);
        }
    }

    private void dispatch(Runnable work) {
        try {
            executor.execute(() -> perform(work));
        } catch (RejectedExecutionException e) {
            LOG.error("Could not queue background work", e);
        }
    }

    // Deliberately broad: whatever the work throws must end here, not in the
    // executor's default handler, and not differently for different addresses.
    private void perform(Runnable work) {
        try {
            transactions.executeWithoutResult(status -> work.run());
        } catch (RuntimeException e) {
            LOG.error("Background work failed", e);
        }
    }
}
