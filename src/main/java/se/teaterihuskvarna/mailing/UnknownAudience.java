package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// The audience is not one [MailingService#audiences] offers. The adapters answer 400.
public class UnknownAudience extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    UnknownAudience(String message) {
        super(message);
    }
}
