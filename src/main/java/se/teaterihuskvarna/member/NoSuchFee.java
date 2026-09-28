package se.teaterihuskvarna.member;

import java.io.Serial;

/// The member has no fee payment of their own for this year to undo. A
/// household payment by someone else is undone on the payer. The adapters
/// answer 404.
public class NoSuchFee extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoSuchFee() {
        super("the member has no fee payment of their own for this year");
    }
}
