package se.teaterihuskvarna.login;

import java.util.Locale;

/// An email address, trimmed and in lower case. Addresses are compared case
/// insensitively everywhere, as the unique indexes on `LOWER(email)` are.
/// Normalising in the constructor means every lookup, rate limit count and
/// stored row gets the same string, and `Anna@` and `anna@` count as one
/// address.
///
/// Normalising is all it does. Whether the text is an address at all is for the
/// form's `@Email` to say, with a message for the field.
///
/// @param value the address, normalised
public record Email(String value) {

    /// @param value an address as somebody typed it
    public Email {
        value = value.strip().toLowerCase(Locale.ROOT);
    }
}
