package se.teaterihuskvarna.offer;

/// What [OfferService#register] did.
public enum RegistrationOutcome {

    /// The member took a place.
    REGISTERED,

    /// The member already had a place, and nothing changed.
    ALREADY_REGISTERED
}
