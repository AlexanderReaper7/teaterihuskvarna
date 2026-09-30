package se.teaterihuskvarna.document;

import java.io.Serial;

/// No member document has the id. The adapters answer 404.
public class NoSuchDocument extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchDocument() {
        super("no such document");
    }
}
