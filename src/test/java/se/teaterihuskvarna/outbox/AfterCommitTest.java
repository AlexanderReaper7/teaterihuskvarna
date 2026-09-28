package se.teaterihuskvarna.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/// Drives the synchronization by hand, as a transaction manager would. The
/// integration tests cannot see the order: the executor that [Outbox] and
/// `Background` hand the work to nearly always starts after the commit anyway.
class AfterCommitTest {

    private final AtomicInteger runs = new AtomicInteger();

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void outsideATransactionTheWorkRunsAtOnce() {
        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void insideATransactionTheWorkWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        TransactionSynchronizationUtils.triggerAfterCommit();

        assertThat(runs).hasValue(1);
    }

    @Test
    void aRollbackRunsNothing() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.run(runs::incrementAndGet);

        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(runs).hasValue(0);
    }
}
