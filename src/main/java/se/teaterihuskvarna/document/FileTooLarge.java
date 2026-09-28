package se.teaterihuskvarna.document;

import java.io.Serial;

/// The upload is over [MemberDocumentService#MAX_BYTES]. The adapters answer 413.
public class FileTooLarge extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    FileTooLarge() {
        super("the file is larger than 10 MB");
    }
}
