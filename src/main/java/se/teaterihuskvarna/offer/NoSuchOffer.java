package se.teaterihuskvarna.offer;

import java.io.Serial;

/// No offer has the id, or, for a member, none that is published. The adapters
/// answer 404, so a member cannot tell an unpublished offer from a missing one.
public class NoSuchOffer extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchOffer() {
        super("no such offer");
    }
}
