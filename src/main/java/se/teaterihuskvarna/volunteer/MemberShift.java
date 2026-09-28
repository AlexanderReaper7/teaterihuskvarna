package se.teaterihuskvarna.volunteer;

import java.time.Instant;

/// A shift as a member sees it: R016.
///
/// @param id         the shift
/// @param eventTitle the event it is at
/// @param eventSlug  the event's address, `/evenemang/<slug>`
/// @param task       the work
/// @param startsAt   when it starts
/// @param endsAt     when it ends
/// @param placesLeft how many more members can book it
/// @param booked     whether this member booked it
public record MemberShift(long id, String eventTitle, String eventSlug, Task task, Instant startsAt,
        Instant endsAt, int placesLeft, boolean booked) {
}
