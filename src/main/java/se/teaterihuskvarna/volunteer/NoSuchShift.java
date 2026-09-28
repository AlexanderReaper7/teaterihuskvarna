package se.teaterihuskvarna.volunteer;

import java.io.Serial;

/// No shift has the id. The adapters answer 404.
public class NoSuchShift extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchShift(String message) {
        super(message);
    }
}
