package se.teaterihuskvarna.volunteer;

import java.io.Serial;

/// The shift has started, so it can no longer be booked or cancelled. The adapters answer 409.
public class ShiftStarted extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    ShiftStarted(String message) {
        super(message);
    }
}
