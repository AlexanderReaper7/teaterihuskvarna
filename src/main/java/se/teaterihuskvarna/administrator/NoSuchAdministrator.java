package se.teaterihuskvarna.administrator;

import java.io.Serial;

/// The id names no active administrator: it never existed, or the account was
/// already removed. The adapters answer 404.
public class NoSuchAdministrator extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchAdministrator() {
        super("no active administrator has this id");
    }
}
