package se.teaterihuskvarna.mailing;

import java.io.Serial;

/// No one in the audience chosen for a mailing can receive it: the list or
/// segment holds no member, or every member in it has unsubscribed. In the
/// live check on #60, Brevo made no draft to a list whose only contact had
/// unsubscribed. The adapters answer 409.
public class EmptyAudience extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    EmptyAudience(String message) {
        super(message);
    }
}
