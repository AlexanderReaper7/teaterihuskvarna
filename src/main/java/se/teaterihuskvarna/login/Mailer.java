package se.teaterihuskvarna.login;

import org.springframework.stereotype.Component;
import se.teaterihuskvarna.outbox.Outbox;
import tools.jackson.databind.json.JsonMapper;

/// Sends plain text mail through the [Outbox], never on the request thread, and
/// never lets a failure reach the caller.
///
/// Both rules exist for the same reason: a response must not reveal whether an
/// address has an account. Only a known address gets a mail. If the request
/// waited for the SMTP relay, a known address would answer hundreds of
/// milliseconds slower than an unknown one, and anybody could time the login
/// form to test addresses. If a send failure propagated, a known address would
/// get a 500 while an unknown one got the normal page. The mails that depend on
/// an address are queued from [Background], so even the outbox row is written
/// off the request thread.
///
/// The mail is stored in the caller's transaction and sent after the commit. A
/// mail with a link whose token was rolled back would lead to a login that
/// cannot work, and a mail sent before a commit that then fails is one nobody
/// can take back. A send that fails is tried again later ([MailDelivery]).
@Component
public class Mailer {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final Outbox outbox;

    Mailer(Outbox outbox) {
        this.outbox = outbox;
    }

    /// Queues a mail. Returns before the mail is sent.
    ///
    /// @param to      the recipient's address
    /// @param subject the subject line
    /// @param body    the plain text body
    public void send(Email to, String subject, String body) {
        outbox.add(MailDelivery.KIND, JSON.writeValueAsString(new MailDelivery.Payload(to.value(), subject, body)));
    }
}
