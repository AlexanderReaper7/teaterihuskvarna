package se.teaterihuskvarna.administrator;

import java.io.Serial;

/// The address given for a new administrator already belongs to an active
/// administrator account. The adapters answer 409.
public class AdministratorAlreadyExists extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    AdministratorAlreadyExists() {
        super("an active administrator already has this address");
    }
}
