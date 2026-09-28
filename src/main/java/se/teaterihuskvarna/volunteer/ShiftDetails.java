package se.teaterihuskvarna.volunteer;

import java.time.Instant;

/// A shift as an administrator sees it: R020.
///
/// @param id         the shift
/// @param eventId    the Sanity event's published id
/// @param eventTitle the event's title when the shift was made
/// @param eventSlug  the event's address, `/evenemang/<slug>`
/// @param task       the work
/// @param startsAt   when it starts
/// @param endsAt     when it ends
/// @param places     how many members it needs
/// @param booked     how many have booked it
public record ShiftDetails(long id, String eventId, String eventTitle, String eventSlug, Task task,
        Instant startsAt, Instant endsAt, int places, long booked) {

    static ShiftDetails of(Shift shift, long booked) {
        return new ShiftDetails(shift.getId(), shift.getEventId(), shift.getEventTitle(), shift.getEventSlug(),
                shift.getTask(), shift.getStartsAt(), shift.getEndsAt(), shift.getPlaces(), booked);
    }
}
