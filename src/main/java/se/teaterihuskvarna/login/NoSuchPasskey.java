package se.teaterihuskvarna.login;

import java.io.Serial;

/// The id names no passkey of the logged-in login's: it never existed, it was
/// already removed, or it belongs to someone else. The adapters answer 404
/// for all three, so the answer does not tell whether another person's
/// passkey has that id.
public class NoSuchPasskey extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchPasskey() {
        super("no passkey of this login has this id");
    }
}
