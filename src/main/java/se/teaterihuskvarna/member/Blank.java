package se.teaterihuskvarna.member;

import org.jspecify.annotations.Nullable;

/// A form sends an empty field as an empty string, and the register stores no
/// value as NULL.
final class Blank {

    private Blank() {
    }

    static @Nullable String toNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
