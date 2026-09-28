package se.teaterihuskvarna.volunteer;

import java.io.Serial;

/// Every place on the shift is taken. The adapters answer 409.
public class ShiftFull extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    ShiftFull(String message) {
        super(message);
    }
}
