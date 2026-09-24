package se.teaterihuskvarna.login;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import se.teaterihuskvarna.Swedish;

/// Names the device a login happens on, such as "Firefox på Windows", for the
/// list of logged-in devices ([DeviceService]). Read from the `User-Agent` once,
/// at login, and stored as the name alone.
///
/// The same two tables are in `static/js/passkey.js`, which names a new passkey
/// the same way in the browser. A browser added to one belongs in the other.
final class DeviceNames {

    /// First match wins, so Edge and Opera, whose headers also say Chrome and
    /// Safari, come before them.
    private static final List<Map.Entry<Pattern, String>> BROWSERS = List.of(
            Map.entry(Pattern.compile("Edg/"), "Edge"),
            Map.entry(Pattern.compile("OPR/"), "Opera"),
            Map.entry(Pattern.compile("Firefox/|FxiOS"), "Firefox"),
            Map.entry(Pattern.compile("Chrome/|CriOS"), "Chrome"),
            Map.entry(Pattern.compile("Safari/"), "Safari"));

    /// Android before Linux, whose name Android's header also carries.
    private static final List<Map.Entry<Pattern, String>> SYSTEMS = List.of(
            Map.entry(Pattern.compile("Windows"), "Windows"),
            Map.entry(Pattern.compile("Android"), "Android"),
            Map.entry(Pattern.compile("iPhone"), "iOS"),
            Map.entry(Pattern.compile("iPad"), "iPadOS"),
            Map.entry(Pattern.compile("CrOS"), "ChromeOS"),
            Map.entry(Pattern.compile("Mac OS X"), "macOS"),
            Map.entry(Pattern.compile("Linux"), "Linux"));

    private final MessageSource messages;

    /// @param messages the Swedish copy, which holds the pattern and the word for an unknown part
    DeviceNames(MessageSource messages) {
        this.messages = messages;
    }

    /// @param userAgent the request's `User-Agent`, if it sent one
    /// @return the browser and the system, such as "Firefox på Windows", with "okänd" for a part not recognised
    String of(@Nullable String userAgent) {
        String unknown = messages.getMessage("device.unknown", null, Swedish.LOCALE);
        String agent = userAgent == null ? "" : userAgent;
        return messages.getMessage("device.name",
                new Object[] {find(BROWSERS, agent, unknown), find(SYSTEMS, agent, unknown)}, Swedish.LOCALE);
    }

    private static String find(List<Map.Entry<Pattern, String>> table, String agent, String unknown) {
        for (Map.Entry<Pattern, String> entry : table) {
            if (entry.getKey().matcher(agent).find()) {
                return entry.getValue();
            }
        }
        return unknown;
    }
}
