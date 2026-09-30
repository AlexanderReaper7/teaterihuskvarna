package se.teaterihuskvarna.content;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/// The client IP has exhausted its allowance for preview-secret exchanges.
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyPreviews extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    TooManyPreviews() {
        super("At most 20 preview exchanges per client IP per minute");
    }
}
