package se.teaterihuskvarna.volunteer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

/// What an administrator fills in to add a shift. The times are wall-clock
/// times in Sweden, as the page's `datetime-local` fields send them.
///
/// @param eventId  the Sanity event's published id, one of the upcoming events
/// @param task     the work
/// @param startsAt when the shift starts
/// @param endsAt   when it ends, after the start
/// @param places   how many members it needs
public record ShiftForm(
        @NotBlank(message = "{shift.event.required}")
        String eventId,

        @NotNull(message = "{shift.task.required}")
        @Nullable Task task,

        @NotNull(message = "{shift.startsAt.required}")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable LocalDateTime startsAt,

        @NotNull(message = "{shift.endsAt.required}")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable LocalDateTime endsAt,

        @NotNull(message = "{shift.places.required}")
        @Min(value = 1, message = "{shift.places.min}")
        @Max(value = 100, message = "{shift.places.max}")
        @Nullable Integer places) {

    /// @return an empty form, for the page that adds a shift
    public static ShiftForm empty() {
        return new ShiftForm("", null, null, null, 2);
    }

    /// @return whether the shift ends after it starts, or a time is missing and another message says so
    @AssertTrue(message = "{shift.endsAt.afterStart}")
    public boolean isEndsAfterStart() {
        LocalDateTime start = startsAt;
        LocalDateTime end = endsAt;
        return start == null || end == null || end.isAfter(start);
    }
}
