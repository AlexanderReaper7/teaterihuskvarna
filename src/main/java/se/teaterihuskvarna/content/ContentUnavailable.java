package se.teaterihuskvarna.content;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/// Sanity could not be reached and no earlier copy of the content is kept.
/// Answers 503, which the error page shows as "something went wrong".
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ContentUnavailable extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// @param message what failed, for the log
    /// @param cause   the underlying failure, or null
    ContentUnavailable(String message, Throwable cause) {
        super(message, cause);
    }

    /// @param message what failed, for the log
    ContentUnavailable(String message) {
        super(message);
    }
}
