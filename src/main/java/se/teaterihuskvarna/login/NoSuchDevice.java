package se.teaterihuskvarna.login;

import java.io.Serial;

/// The id names no device the asking person is logged in on: it never existed,
/// that login already ended, or it belongs to someone else. The adapters answer
/// 404 for all three, so the answer does not tell whether another person has a
/// login with that id.
public class NoSuchDevice extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchDevice() {
        super("no device of this login has this id");
    }
}
