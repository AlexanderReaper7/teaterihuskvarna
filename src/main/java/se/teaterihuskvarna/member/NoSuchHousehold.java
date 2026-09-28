package se.teaterihuskvarna.member;

import java.io.Serial;

/// The id names no household. The adapters answer 404.
public class NoSuchHousehold extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchHousehold() {
        super("no household has this id");
    }
}
