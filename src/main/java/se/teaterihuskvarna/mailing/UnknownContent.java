package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// An event or news item the form names is not published, or no longer is. The adapters answer 400.
public class UnknownContent extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    UnknownContent(String message) {
        super(message);
    }
}
