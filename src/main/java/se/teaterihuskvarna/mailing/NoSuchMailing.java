package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// No mailing has the id. The adapters answer 404.
public class NoSuchMailing extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchMailing(String message) {
        super(message);
    }
}
