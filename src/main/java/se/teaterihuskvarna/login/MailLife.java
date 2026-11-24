package se.teaterihuskvarna.login;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// How long a mail is worth sending. [Mailer#send] takes one, so nobody can
/// queue a mail without saying which of the two kinds it is
/// (`docs/decisions/0026-outbox-and-brevo-contacts.md`, "Mail whose content
/// expires").
///
/// The test for a new mail: is there a moment after which sending it would
/// hand the recipient a link or code that no longer works, or tell them
/// something that is no longer true? Then it is [#until] that moment.
/// Otherwise it is [#untilSent], also when it would only arrive late.
public final class MailLife {

    private static final MailLife UNTIL_SENT = new MailLife(null);

    private final @Nullable Instant notAfter;

    private MailLife(@Nullable Instant notAfter) {
        this.notAfter = notAfter;
    }

    /// For a mail whose text stays true however late it arrives. It is retried
    /// until it is sent, which is the user's rule for mail.
    ///
    /// @return the life of a mail that never expires
    public static MailLife untilSent() {
        return UNTIL_SENT;
    }

    /// For a mail whose text stops being true at a known moment: a link or a
    /// code that expires, or a day the text names as tomorrow. It is retried
    /// until that moment, and after it the mail is deleted unsent.
    ///
    /// @param notAfter the moment the text stops being true, taken from what
    ///                 makes it so, such as the link's own expiry
    /// @return the life of a mail that expires then
    public static MailLife until(Instant notAfter) {
        return new MailLife(Objects.requireNonNull(notAfter));
    }

    /// @return the moment after which the mail is not sent, or null for never
    @Nullable Instant notAfter() {
        return notAfter;
    }
}
