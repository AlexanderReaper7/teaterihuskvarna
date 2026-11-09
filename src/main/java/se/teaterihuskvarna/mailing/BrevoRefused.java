package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// Brevo answered a call with an error, 4xx, so it did not do what was asked.
/// A timeout or a 5xx leaves that unknown and is a plain [BrevoUnavailable].
final class BrevoRefused extends BrevoUnavailable {

    @Serial
    private static final long serialVersionUID = 1L;

    /// @param message what failed, for the log
    /// @param cause   Brevo's answer
    BrevoRefused(String message, Throwable cause) {
        super(message, cause);
    }

    /// @param message what failed, for the log
    BrevoRefused(String message) {
        super(message);
    }
}
