package se.teaterihuskvarna.offer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

/// What an administrator fills in to create or change an offer. The limits
/// are the column widths in `offer`, and the messages are keys in
/// `messages_sv.properties`.
///
/// The times are wall-clock times in Sweden, such as `2026-10-05T19:00`,
/// because that is what the page's `datetime-local` fields send and what an
/// administrator means. The service turns them into instants with
/// Europe/Stockholm; the details records give instants back.
///
/// @param title                the offer's title, required
/// @param description          plain text shown with its line breaks, or null or blank for none
/// @param startsAt             when the offer happens, or null
/// @param registrationClosesAt when registration and cancellation close, or null to close at `startsAt`
/// @param capacity             the number of places, or null for no limit
public record OfferForm(
        @NotBlank(message = "{offer.title.required}")
        @Size(max = 200, message = "{offer.title.size}")
        String title,

        @Size(max = DESCRIPTION_MAX, message = "{offer.description.size}")
        @Nullable String description,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable LocalDateTime startsAt,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable LocalDateTime registrationClosesAt,

        @Min(value = 0, message = "{offer.capacity.min}")
        @Max(value = CAPACITY_MAX, message = "{offer.capacity.max}")
        @Nullable Integer capacity) {

    /// The longest description the service accepts, and the length of the
    /// `VARCHAR` column in V8.
    public static final int DESCRIPTION_MAX = 10_000;

    /// The most places an offer can have. Above this is a typing mistake.
    public static final int CAPACITY_MAX = 100_000;

    /// Sweden, where every offer takes place.
    static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    /// @return an empty form, for the page that creates an offer
    public static OfferForm empty() {
        return new OfferForm("", "", null, null, null);
    }

    /// @return the values to store, with the times as instants
    OfferValues values() {
        return new OfferValues(
                title.strip(),
                description == null ? "" : description.strip(),
                instant(startsAt),
                instant(registrationClosesAt),
                capacity);
    }

    /// @param time a moment
    /// @return the wall-clock time in Sweden at that moment to the minute, as the
    ///         form's fields take it, or null for null. A `datetime-local` field
    ///         drops a value with more than three decimals and is sent empty.
    static @Nullable LocalDateTime local(@Nullable Instant time) {
        return time == null ? null : LocalDateTime.ofInstant(time, SWEDEN).truncatedTo(ChronoUnit.MINUTES);
    }

    private static @Nullable Instant instant(@Nullable LocalDateTime time) {
        return time == null ? null : time.atZone(SWEDEN).toInstant();
    }
}
