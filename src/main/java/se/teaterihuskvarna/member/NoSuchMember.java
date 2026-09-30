package se.teaterihuskvarna.member;

import java.io.Serial;

/// The id names no member the caller may act on: it never existed, it was
/// deleted, or, for a member acting on their household, it is not in their
/// household. The adapters answer 404 in every case, so a member cannot probe
/// which ids exist.
public class NoSuchMember extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchMember() {
        super("no member the caller may act on has this id");
    }
}
