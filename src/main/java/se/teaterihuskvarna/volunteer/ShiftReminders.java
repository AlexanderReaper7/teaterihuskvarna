package se.teaterihuskvarna.volunteer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// Runs R017's reminder every hour from 09:00 to 21:00 Swedish time. The first
/// run of the day reminds everyone booked for tomorrow; the later ones catch
/// bookings made during the day. No mail goes out at night: a booking made
/// after the last run, for a shift tomorrow, is reminded when it is made
/// ([ShiftService#book]), since the next run may come after the shift starts.
/// The cron's last hour is [ShiftService#LAST_RUN].
///
/// Also reports each shift once it has started, every five minutes, so the
/// Brevo contacts' LAST_SHIFT follows ([ShiftService#reportStartedShifts]).
@Component
class ShiftReminders {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftReminders.class);

    private final ShiftService shifts;

    ShiftReminders(ShiftService shifts) {
        this.shifts = shifts;
    }

    @Scheduled(cron = "0 0 9-21 * * *", zone = "Europe/Stockholm")
    void remind() {
        int sent = shifts.sendReminders();
        if (sent > 0) {
            LOG.info("Queued {} volunteer shift reminders", sent);
        }
    }

    @Scheduled(fixedDelayString = "PT5M")
    void reportStarts() {
        shifts.reportStartedShifts();
    }
}
