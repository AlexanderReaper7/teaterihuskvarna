package se.teaterihuskvarna.content;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/// A webhook call whose `sanity-webhook-signature` does not match its body,
/// or which arrived while no secret is configured.
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidSignature extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidSignature() {
        super("The webhook signature does not match");
    }
}
