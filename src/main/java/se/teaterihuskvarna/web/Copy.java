package se.teaterihuskvarna.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.context.MessageSource;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.login.Lifetimes;

/// Fixed Swedish copy, for templates to read by key. Every page gets one as the
/// model attribute `copy`, from [PageModel].
///
/// A template therefore holds `${copy.text("login.email")}` and never the Swedish
/// itself, as `docs/decisions/0001-language-policy.md` requires. The alternative,
/// a controller putting each string in the model, means a form page with twenty
/// labels has twenty `addAttribute` lines and twenty `@param` lines that say nothing.
///
/// Always Swedish, whatever the browser asks for: there is one bundle.
public final class Copy {

    /// The association is in Huskvarna, so a date is the date there, whatever
    /// zone the container runs in.
    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Swedish.LOCALE)
            .withZone(SWEDEN);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("d MMMM yyyy 'kl.' HH:mm", Swedish.LOCALE)
            .withZone(SWEDEN);

    private final MessageSource messages;

    Copy(MessageSource messages) {
        this.messages = messages;
    }

    /// A key with no arguments comes back exactly as written in the file. A key with
    /// arguments goes through `MessageFormat`, where a literal apostrophe is `''`.
    /// That split is Spring's default, `alwaysUseMessageFormat` being false.
    ///
    /// @param key  a key in `messages_sv.properties`
    /// @param args values for `{0}`, `{1}` and so on
    /// @return the Swedish text
    public String text(String key, Object... args) {
        return messages.getMessage(key, args, Swedish.LOCALE);
    }

    /// @param lifetime how long a link works
    /// @return the lifetime in Swedish words, such as "en timme"
    public String duration(Duration lifetime) {
        return Lifetimes.describe(messages, lifetime);
    }

    /// @param instant a moment
    /// @return its date in Sweden, such as "23 september 2026"
    public String date(Instant instant) {
        return DATE.format(instant);
    }

    /// @param instant a moment
    /// @return its date and time in Sweden, such as "23 september 2026 kl. 14:05"
    public String dateTime(Instant instant) {
        return DATE_TIME.format(instant);
    }
}
