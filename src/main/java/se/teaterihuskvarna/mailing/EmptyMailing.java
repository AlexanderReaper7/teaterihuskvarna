package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// The mailing has neither words of its own nor any content from Sanity. The adapters answer 400.
public class EmptyMailing extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    EmptyMailing(String message) {
        super(message);
    }
}
