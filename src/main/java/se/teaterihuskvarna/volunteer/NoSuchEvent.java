package se.teaterihuskvarna.volunteer;

import java.io.Serial;

/// The event a new shift names is not published, or has passed. The adapters answer 400.
public class NoSuchEvent extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchEvent(String message) {
        super(message);
    }
}
