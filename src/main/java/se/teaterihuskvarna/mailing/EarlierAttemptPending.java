package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// A mailing cannot be prepared while an earlier one is still `creating`,
/// since Brevo may yet make the earlier draft, and a second draft of the
/// same mail could then be sent twice. The database allows one such mailing
/// at a time. The page shows the form again with the reason, and the API answers
/// 409.
public class EarlierAttemptPending extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// @param message what failed, for the log
    /// @param cause   the refused insert
    EarlierAttemptPending(String message, Throwable cause) {
        super(message, cause);
    }
}
