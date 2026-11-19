package se.teaterihuskvarna.member;

import java.io.Serial;

/// A member already has a household. Creation must not replace it.
public class AlreadyInHousehold extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    AlreadyInHousehold() {
        super("member already belongs to a household");
    }
}
