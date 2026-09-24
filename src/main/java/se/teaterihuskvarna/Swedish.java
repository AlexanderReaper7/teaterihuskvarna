package se.teaterihuskvarna;

import java.util.Locale;

/// The site's one language. Every text comes from `messages_sv.properties` in
/// this locale, whatever the browser asks for.
public final class Swedish {

    /// Swedish as written in Sweden.
    public static final Locale LOCALE = Locale.of("sv", "SE");

    private Swedish() {
    }
}
