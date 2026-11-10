package se.teaterihuskvarna.login;

import java.time.Instant;
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
///
/// Every mail is one of two kinds, and the caller says which by the [#send]
/// it calls. A mail whose text stays true is retried until it is sent, which
/// is what the user decided for mail. A mail whose text stops being true, a
/// link or a code that expires or a day named as tomorrow, is given that
/// moment and is not sent after it, an exception an agent made after the
/// review on 2026-11-10. The table of which mail is which is in
/// `docs/decisions/0026-outbox-and-brevo-contacts.md`.
@Component
public class Mailer {

    private static final JsonMapper JSON = JsonMapper.shared();

    private final Outbox outbox;

    Mailer(Outbox outbox) {
        this.outbox = outbox;
    }

    /// Queues a mail whose text stays true however late it arrives. It is
    /// retried until it is sent. Returns before the mail is sent.
    ///
    /// @param to      the recipient's address
    /// @param subject the subject line
    /// @param body    the plain text body
    public void send(Email to, String subject, String body) {
        outbox.add(MailDelivery.KIND, payload(to, subject, body));
    }

    /// Queues a mail whose text stops being true at a known moment: a link or
    /// a code that expires, or a day the text names as tomorrow. It is retried
    /// until that moment, and after it the mail is deleted unsent. Returns
    /// before the mail is sent.
    ///
    /// @param to       the recipient's address
    /// @param subject  the subject line
    /// @param body     the plain text body
    /// @param notAfter the moment the text stops being true, taken from what
    ///                 makes it so, such as the link's own expiry
    public void send(Email to, String subject, String body, Instant notAfter) {
        outbox.add(MailDelivery.KIND, payload(to, subject, body), notAfter);
    }

    private static String payload(Email to, String subject, String body) {
        return JSON.writeValueAsString(new MailDelivery.Payload(to.value(), subject, body));
    }
}
