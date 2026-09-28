package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// The audience has nobody with an account, so there is no one to send to. The adapters answer 409.
public class NoRecipients extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    NoRecipients(String message) {
        super(message);
    }
}
