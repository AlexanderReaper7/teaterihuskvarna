package se.teaterihuskvarna.login;

import java.time.Duration;
import org.springframework.context.MessageSource;
import se.teaterihuskvarna.Swedish;

/// A lifetime in Swedish words, for the mail that says how long a link works and
/// the page that repeats it. The lifetimes are configurable, so the text cannot
/// be fixed copy.
public final class Lifetimes {

    private Lifetimes() {
    }

    /// @param messages the Swedish copy, which holds the singular and plural forms
    /// @param lifetime the lifetime to describe
    /// @return the lifetime in the largest whole unit, such as "en timme" or "7 dygn"
    public static String describe(MessageSource messages, Duration lifetime) {
        if (lifetime.toHours() > 0 && lifetime.toHoursPart() == 0 && lifetime.toMinutesPart() == 0) {
            return messages.getMessage("duration.days", new Object[] {lifetime.toDays()}, Swedish.LOCALE);
        }
        if (lifetime.toMinutesPart() == 0 && lifetime.toHours() > 0) {
            return messages.getMessage("duration.hours", new Object[] {lifetime.toHours()}, Swedish.LOCALE);
        }
        return messages.getMessage("duration.minutes", new Object[] {lifetime.toMinutes()}, Swedish.LOCALE);
    }
}
