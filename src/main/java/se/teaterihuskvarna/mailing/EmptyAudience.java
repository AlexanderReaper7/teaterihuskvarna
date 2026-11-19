package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// No one in the audience chosen for a mailing can receive it: the list or
/// segment holds no member, or every member in it has unsubscribed. Brevo
/// refuses a draft to such an audience with 400 `invalid_parameter`, "There
/// are no contacts associated with the given recipients info", as measured
/// on 2026-11-04. The page shows the form again with the reason, and the API
/// answers 409.
public class EmptyAudience extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    EmptyAudience(String message) {
        super(message);
    }
}
