package se.teaterihuskvarna.login;

import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/// Sends plain text mail, never on the request thread, and never lets a failure
/// reach the caller.
///
/// Both rules exist for the same reason: a response must not reveal whether an
/// address has an account. Only a known address gets a mail. If the request
/// waited for the SMTP relay, a known address would answer hundreds of
/// milliseconds slower than an unknown one, and anybody could time the login
/// form to test addresses. If a send failure propagated, a known address would
/// get a 500 while an unknown one got the normal page. So the send runs on
/// Spring Boot's `applicationTaskExecutor`, and a failure is logged and dropped.
/// The person who asked for the link sees nothing arrive and can ask again.
///
/// Inside a transaction the send waits for the commit. A mail with a link whose
/// token was rolled back would lead to a login that cannot work, and a mail sent
/// before a commit that then fails is one nobody can take back.
@Component
public class Mailer {

    private static final Logger LOG = LoggerFactory.getLogger(Mailer.class);

    private final JavaMailSender sender;
    private final MailSettings settings;
    private final TaskExecutor executor;

    Mailer(JavaMailSender sender, MailSettings settings,
            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.sender = sender;
        this.settings = settings;
        this.executor = executor;
    }

    /// Queues a mail. Returns before the mail is sent, and after the current
    /// transaction commits if there is one.
    ///
    /// @param to      the recipient's address
    /// @param subject the subject line
    /// @param body    the plain text body
    public void send(Email to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(settings.from());
        message.setTo(to.value());
        message.setSubject(subject);
        message.setText(body);
        AfterCommit.run(() -> dispatch(message));
    }

    /// A full queue would otherwise throw on the request thread, and only for a
    /// known address, which is the difference this class exists to hide.
    private void dispatch(SimpleMailMessage message) {
        try {
            executor.execute(() -> deliver(message));
        } catch (RejectedExecutionException e) {
            LOG.error("Could not queue a mail", e);
        }
    }

    /// Runs on the executor. The recipient stays out of the log, which is not
    /// the place to keep who asked for a login link.
    private void deliver(SimpleMailMessage message) {
        try {
            sender.send(message);
        } catch (MailException e) {
            LOG.error("Could not send a mail", e);
        }
    }
}
