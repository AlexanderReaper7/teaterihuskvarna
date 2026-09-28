package se.teaterihuskvarna.volunteer;

/// What booking a shift did.
public enum BookingOutcome {
    /// The member took a place.
    BOOKED,
    /// The member already had one, and nothing changed.
    ALREADY_BOOKED
}
