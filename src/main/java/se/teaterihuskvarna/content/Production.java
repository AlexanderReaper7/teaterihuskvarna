package se.teaterihuskvarna.content;

import org.jspecify.annotations.Nullable;

/// A production the association has played, from Sanity's `produktion` type:
/// R004, on the page Produktioner.
///
/// @param title     its title
/// @param firstYear the year it opened, or null when the editors have not given one
/// @param lastYear  the last year it played, or null when it played only `firstYear`
/// @param image     its picture, or null
/// @param bodyHtml  the description as HTML, escaped by [PortableText] and safe to print as it is
public record Production(String title, @Nullable Integer firstYear, @Nullable Integer lastYear,
        @Nullable Image image, String bodyHtml) {
}
