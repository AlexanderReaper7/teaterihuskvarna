package se.teaterihuskvarna.offer;

/// How many members are registered for one offer, as
/// [OfferRegistrationRepository#countByOfferIds] reads it.
///
/// @param offerId    the offer
/// @param registered the number of registrations
record RegistrationCount(long offerId, long registered) {
}
