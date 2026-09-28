---
created: 2026-09-28
provenance: agent
description: How volunteer shifts, bookings and reminders work.
---

# 0024: Volunteer shifts belong to a Sanity event and live in PostgreSQL

Written by an agent on 2026-09-28. The user has not reviewed any of it.

## Decision

- A shift (R016) is a row in `volunteer_shift` that names a published Sanity event by its document id and keeps a copy of the event's title and slug. The copy lets the list of shifts and the reminder work when Sanity is down, or after an editor unpublishes the event.
- A shift has one task, cloakroom (`GARDEROB`) or serving (`SERVERING`), the two R016 names, and a number of places. A new task means a new enum value and a migration that widens the check constraint.
- Only an upcoming published event can get a shift. An administrator adds shifts in `/admin/volontarpass`.
- A member books and cancels until the shift starts. Booking locks the shift row, so the places hold however many book at once.
- The reminder (R017) goes by email to each booked member's account address the day before the shift. A scheduled job runs every hour from 09:00 to 21:00 Swedish time and mails every booking for tomorrow that has no reminder yet, so a booking made the day before still gets one, and a restart does not send twice. A booking made after the 21:00 run for a shift tomorrow gets its reminder when it is made, since the next run may come after a shift at 08:00 has started. A booking the runs missed, such as during a restart, gets it on the shift's own day if the shift has not started. A booking made on the shift's own day gets none. Changed on 2026-09-28 and 2026-09-29 after the GPT-6 Sol reviews found that bookings made after 21:00 got no reminder at all, and then none for an early shift.
- Deleting a shift deletes its bookings and tells nobody. Mailing the volunteers about a cancelled shift is left out until someone asks for it.
- The export (R020) is a CSV file of who booked a shift, in the same format as the offer export. The administrator's page lists past shifts under the upcoming ones, so a passed shift's bookings can still be found and exported.
