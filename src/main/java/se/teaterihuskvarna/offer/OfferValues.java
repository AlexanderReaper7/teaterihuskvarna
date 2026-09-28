package se.teaterihuskvarna.offer;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// An [OfferForm] after validation, with its times as instants.
///
/// @param title                the title, stripped
/// @param description          the plain-text description, empty for none
/// @param startsAt             when the offer happens, or null
/// @param registrationClosesAt when registration closes, or null
/// @param capacity             the number of places, or null for no limit
record OfferValues(String title, String description, @Nullable Instant startsAt,
        @Nullable Instant registrationClosesAt, @Nullable Integer capacity) {
}
