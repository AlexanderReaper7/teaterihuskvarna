package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// Brevo did not answer whether it made a mailing's draft, and the campaign
/// was not found yet. The mailing stays in the log as `creating` until the
/// campaign is found by its name, or [MailingService#UNANSWERED] has passed
/// without it. The page says so, and the API answers 502 like any
/// [BrevoUnavailable].
public class DraftUnanswered extends BrevoUnavailable {

    @Serial
    private static final long serialVersionUID = 1L;

    /// @param message what failed, for the log
    /// @param cause   the failure that left the answer unknown
    DraftUnanswered(String message, Throwable cause) {
        super(message, cause);
    }

    /// @param message what failed, for the log
    DraftUnanswered(String message) {
        super(message);
    }
}
