package se.teaterihuskvarna.member;

import java.io.Serial;

/// The member already has a fee payment of their own for this year. Undo it
/// first to change the kind or the amount. The adapters answer 409.
public class FeeAlreadyMarked extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    FeeAlreadyMarked() {
        super("the member already has a fee payment for this year");
    }
}
