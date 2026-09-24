package se.teaterihuskvarna.login;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// Holds work back until the caller's transaction commits, so it sees what the
/// caller wrote and never runs for a transaction that rolls back. Outside a
/// transaction the work runs at once. [Mailer] and [Background] queue through
/// this.
final class AfterCommit {

    private AfterCommit() {
    }

    /// @param work what to run after the commit, on the thread that commits
    static void run(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }
}
