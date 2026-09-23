package se.teaterihuskvarna.login;

import java.util.Locale;

/// Email addresses are compared case insensitively everywhere, as the unique
/// indexes on `LOWER(email)` are. Normalising once, on the way in, means the
/// rate limit counts `Anna@` and `anna@` as the same address.
public final class Addresses {

    private Addresses() {
    }

    /// @param email an address as somebody typed it
    /// @return the address trimmed and in lower case
    public static String normalise(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
