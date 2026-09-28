package se.teaterihuskvarna.member;

import java.io.Serial;

/// The address already belongs to another member's account. Addresses are
/// unique across accounts in any case, by the index `account_email_key`. The
/// adapters answer 409.
public class EmailTaken extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    EmailTaken() {
        super("another account already has this address");
    }

    /// @param cause the unique index refusing the address, when two requests raced past the check
    EmailTaken(Throwable cause) {
        super("another account already has this address", cause);
    }
}
