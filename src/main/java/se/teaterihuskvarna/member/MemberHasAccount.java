package se.teaterihuskvarna.member;

import java.io.Serial;

/// An invitation was asked for a member who already has an account. The
/// adapters answer 409.
public class MemberHasAccount extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    MemberHasAccount() {
        super("the member already has an account");
    }
}
