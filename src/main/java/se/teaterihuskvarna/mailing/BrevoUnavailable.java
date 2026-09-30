package se.teaterihuskvarna.mailing;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/// Brevo could not be reached or refused a call. Answers 502, since the fault
/// is in the service behind this one.
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class BrevoUnavailable extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// @param message what failed, for the log
    /// @param cause   the underlying failure
    BrevoUnavailable(String message, Throwable cause) {
        super(message, cause);
    }

    /// @param message what failed, for the log
    BrevoUnavailable(String message) {
        super(message);
    }
}
