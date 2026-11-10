package se.teaterihuskvarna.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import jakarta.mail.SendFailedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
/// - A failed attempt is logged with the classes of what failed and never
///   with a message, since the relay's message names the recipient.
/// - A mail with a `not_after` is retried until then, and after it deleted
///   unsent and never handed to the relay. A mail without one is retried
///   until it is sent.
@ExtendWith(OutputCaptureExtension.class)
class OutboxIT extends IntegrationTestSupport {

    private static final String REFUSED = "refused-recipient@example.test";
    private static final String EXPIRED = "expired-mail@example.test";

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
        assertThat(notAfterOfTheMailInTheOutbox()).isNull();
        assertThat(jdbc.sql("SELECT last_error FROM outbox").query(String.class).single())
                .isEqualTo(MailSendException.class.getName());
        assertThat(jdbc.sql("SELECT next_attempt_at > now() FROM outbox").query(Boolean.class).single()).isTrue();

        forgetMails();
        makeDue();
        outbox.retry();
        assertThat(awaitMail().getSubject()).isEqualTo("Hej");
        awaitEmpty();
    }

    /// The relay's answer as Spring hands it on: the reason per message, with
    /// the address in its text, and in the text of the exception around it.
    @Test
    void aRefusedRecipientStaysOutOfTheLog(CapturedOutput output) {
        SendFailedException reason = new SendFailedException("Invalid Addresses: 550 5.1.1 <" + REFUSED + ">");
        MailSendException refusal = new MailSendException(Map.of(new SimpleMailMessage(), reason));
        assertThat(refusal.getMessage()).contains(REFUSED);
        doThrow(refusal).when(mailSender).send(any(SimpleMailMessage.class));

        mailer.send(new Email(REFUSED), "Hej", "Text");
        awaitAttempts(1);

        long id = jdbc.sql("SELECT id FROM outbox").query(Long.class).single();
        assertThat(output)
                .contains("Outbox row " + id + " of kind mail failed, attempt 1")
                .contains(MailSendException.class.getName())
                .contains(SendFailedException.class.getName())
                .contains("at se.teaterihuskvarna.login.MailDelivery.handle(")
                .doesNotContain(REFUSED)
                .doesNotContain("Invalid Addresses");
    }

    /// Retried until it is sent, as the user decided: never deleted unsent.
    @Test
    void aMailThatKeepsFailingIsNeverGivenUp(CapturedOutput output) {
        doThrow(new MailSendException("relay refused " + REFUSED)).when(mailSender)
                .send(any(SimpleMailMessage.class));

        mailer.send(new Email("karin@example.test"), "Hej", "Text");
        awaitAttempts(1);
        for (int attempt = 2; attempt <= Outbox.MAX_ATTEMPTS + 2; attempt++) {
            makeDue();
            outbox.retry();
            awaitAttempts(attempt);
        }
        assertThat(jdbc.sql("SELECT next_attempt_at > now() + interval '5 hours' FROM outbox")
                .query(Boolean.class).single()).isTrue();
        assertThat(output)
                .contains("of kind mail has failed " + Outbox.MAX_ATTEMPTS + " times; still trying every PT6H")
                .contains(MailSendException.class.getName())
                .doesNotContain(REFUSED);

        doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        forgetMails();
        makeDue();
        outbox.retry();
        assertThat(awaitMail().getSubject()).isEqualTo("Hej");
        awaitEmpty();
    }

    /// The mail waits for its next attempt, which is not due. The retry job
    /// deletes it all the same, so the payload does not outlive the link in it.
    @Test
    void aMailPastItsNotAfterIsDeletedWithoutWaitingForItsNextAttempt(CapturedOutput output) {
        doThrow(new MailSendException("relay down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        mailer.send(new Email(EXPIRED), "Hej", "Text", Instant.now().plus(Duration.ofHours(1)));
        awaitAttempts(1);
        long id = jdbc.sql("SELECT id FROM outbox").query(Long.class).single();
        forgetMails();
        jdbc.sql("UPDATE outbox SET not_after = now() - interval '1 second'").update();

        outbox.retry();

        assertThat(rowsIn("outbox")).isZero();
        assertNoMail();
        assertThat(output)
                .contains("Outbox row " + id + " of kind mail passed its not_after undone and was deleted")
                .doesNotContain(EXPIRED);
    }

    /// The attempt after the commit finds the time already passed, as an
    /// attempt does that waited in the queue behind a slow relay.
    @Test
    void aMailPastItsNotAfterIsNeverHandedToTheRelay() {
        mailer.send(new Email(EXPIRED), "Hej", "Text", Instant.now().minusSeconds(1));

        awaitEmpty();
        assertNoMail();
    }

    @Test
    void aMailBeforeItsNotAfterIsStillRetried() {
        doThrow(new MailSendException("relay down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));
        mailer.send(new Email("karin@example.test"), "Hej", "Text", Instant.now().plus(Duration.ofHours(1)));
        awaitAttempts(1);

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
