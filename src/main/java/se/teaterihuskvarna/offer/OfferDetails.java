package se.teaterihuskvarna.offer;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// An offer as an administrator sees it, published or not.
///
/// @param id                   the offer's id
/// @param title                the title
/// @param description          plain text, shown with its line breaks
/// @param startsAt             when the offer happens, or null
/// @param registrationClosesAt the closing time the administrator set, or null
/// @param closesAt             when registration actually closes, which falls back to `startsAt`, or null for never
/// @param capacity             the number of places, or null for no limit
/// @param published            whether members can see it
/// @param registered           how many members are registered
/// @param placesLeft           the places not taken, never below 0, or null for no limit
/// @param open                 whether registration is still possible
/// @param createdAt            when it was created
/// @param updatedAt            when it was last changed
public record OfferDetails(long id, String title, String description, @Nullable Instant startsAt,
        @Nullable Instant registrationClosesAt, @Nullable Instant closesAt, @Nullable Integer capacity,
        boolean published, long registered, @Nullable Integer placesLeft, boolean open, Instant createdAt,
        Instant updatedAt) {

    static OfferDetails of(Offer offer, long registered, Instant now) {
        return new OfferDetails(
                offer.getId(),
                offer.getTitle(),
                offer.getDescription(),
                offer.getStartsAt(),
                offer.getRegistrationClosesAt(),
                offer.closesAt(),
                offer.getCapacity(),
                offer.isPublished(),
                registered,
                offer.placesLeft(registered),
                offer.isOpenAt(now),
                offer.getCreatedAt(),
                offer.getUpdatedAt());
    }

    /// @return the form an administrator edits, with the times in Swedish wall-clock time
    public OfferForm toForm() {
        return new OfferForm(title, description, OfferForm.local(startsAt), OfferForm.local(registrationClosesAt),
                capacity);
    }
}
