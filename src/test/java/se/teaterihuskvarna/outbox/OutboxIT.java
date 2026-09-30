package se.teaterihuskvarna.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.login.Email;
import se.teaterihuskvarna.login.Mailer;

/// Proves the outbox through its first user, the mail.
///
/// - A mail is sent after the commit, and not at all when the transaction
///   rolls back.
/// - A mail the relay refuses stays in the table and goes out on a retry.
/// - A mail that keeps failing is tried every six hours until it goes out.
class OutboxIT extends IntegrationTestSupport {

    @Autowired
    private Mailer mailer;

    @Autowired
    private Outbox outbox;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void aMailGoesOutAfterTheCommitAndNotAfterARollback() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            mailer.send(new Email("karin@example.test"), "Rolled back", "Nothing");
            status.setRollbackOnly();
        });
        assertNoMail();
        assertThat(rowsIn("outbox")).isZero();

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                mailer.send(new Email("karin@example.test"), "Hej", "Text"));
        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getTo()).containsExactly("karin@example.test");
        assertThat(mail.getSubject()).isEqualTo("Hej");
        assertThat(rowsIn("outbox")).isZero();
    }

    @Test
    void aRefusedMailIsTriedAgain() {
        doThrow(new MailSendException("relay down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send(new Email("karin@example.test"), "Hej", "Text");
        awaitAttempts(1);
        assertThat(jdbc.sql("SELECT last_error FROM outbox").query(String.class).single())
                .isEqualTo(MailSendException.class.getName());
        assertThat(jdbc.sql("SELECT next_attempt_at > now() FROM outbox").query(Boolean.class).single()).isTrue();

        forgetMails();
        makeDue();
        outbox.retry();
        assertThat(awaitMail().getSubject()).isEqualTo("Hej");
        awaitEmpty();
    }

    /// Retried until it is sent, as the user decided: never deleted unsent.
    @Test
    void aMailThatKeepsFailingIsNeverGivenUp() {
        doThrow(new MailSendException("relay down")).when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send(new Email("karin@example.test"), "Hej", "Text");
        awaitAttempts(1);
        for (int attempt = 2; attempt <= Outbox.MAX_ATTEMPTS + 2; attempt++) {
            makeDue();
            outbox.retry();
            awaitAttempts(attempt);
        }
        assertThat(jdbc.sql("SELECT next_attempt_at > now() + interval '5 hours' FROM outbox")
                .query(Boolean.class).single()).isTrue();

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        forgetMails();
        makeDue();
        outbox.retry();
        assertThat(awaitMail().getSubject()).isEqualTo("Hej");
        awaitEmpty();
    }

    @Test
    void theWaitDoublesUpToSixHours() {
        assertThat(Outbox.wait(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(Outbox.wait(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(Outbox.wait(9)).isEqualTo(Duration.ofHours(4).plusMinutes(16));
        assertThat(Outbox.wait(10)).isEqualTo(Duration.ofHours(6));
    }

    /// The retry job hands rows to another thread, so the test waits for the
    /// attempt to be recorded.
    private void awaitAttempts(int attempts) {
        await(() -> jdbc.sql("SELECT COALESCE(MAX(attempts), 0) FROM outbox").query(Integer.class).single() == attempts,
                "attempt " + attempts);
    }

    private void awaitEmpty() {
        await(() -> rowsIn("outbox") == 0, "an empty outbox");
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

    private void makeDue() {
        jdbc.sql("UPDATE outbox SET next_attempt_at = now() - interval '1 second'").update();
    }
}
