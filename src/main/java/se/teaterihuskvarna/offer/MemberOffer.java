package se.teaterihuskvarna.offer;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// A published offer as one member sees it. Built inside the transaction, as
/// `docs/decisions/0014-one-service-layer-two-adapters.md` requires.
///
/// @param id          the offer's id
/// @param title       the title
/// @param description plain text, shown with its line breaks
/// @param startsAt    when the offer happens, or null
/// @param closesAt    when registration and cancellation close, or null for never
/// @param capacity    the number of places, or null for no limit
/// @param placesLeft  the places not taken, never below 0, or null for no limit
/// @param registered  whether this member is registered
/// @param open        whether registration and cancellation are still possible
public record MemberOffer(long id, String title, String description, @Nullable Instant startsAt,
        @Nullable Instant closesAt, @Nullable Integer capacity, @Nullable Integer placesLeft, boolean registered,
        boolean open) {

    /// @return whether a member who is not registered can take a place now
    public boolean canRegister() {
        return open && !registered && (placesLeft == null || placesLeft > 0);
    }
}
