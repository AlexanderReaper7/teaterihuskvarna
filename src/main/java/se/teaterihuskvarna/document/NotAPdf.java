package se.teaterihuskvarna.document;

import java.io.Serial;

/// The upload is missing, empty, or does not start with `%PDF-`, whatever type
/// it claimed. The adapters answer 400.
public class NotAPdf extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NotAPdf() {
        super("the file is not a PDF");
    }
}
