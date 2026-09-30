package se.teaterihuskvarna.member;

import java.io.Serial;

/// An edit left the address empty for a member who has an account. The address
/// is the account's login, so it cannot be removed by an edit; deleting the
/// member is the way to remove the account. The adapters answer 409.
public class AccountNeedsEmail extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    AccountNeedsEmail() {
        super("a member with an account must keep an address");
    }
}
