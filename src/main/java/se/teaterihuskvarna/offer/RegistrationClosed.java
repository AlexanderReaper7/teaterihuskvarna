package se.teaterihuskvarna.offer;

import java.io.Serial;

/// Registration has closed, so a member can neither register nor cancel. The
/// adapters answer 409.
public class RegistrationClosed extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    RegistrationClosed() {
        super("registration for the offer has closed");
    }
}
