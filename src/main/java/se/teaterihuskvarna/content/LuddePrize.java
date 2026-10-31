package se.teaterihuskvarna.content;

import org.jspecify.annotations.Nullable;

/// A Ludde prize the association has awarded, from Sanity's `luddepris` type:
/// R004, on the page Ludde-priser.
///
/// @param recipient    the person or group who received it
/// @param year         the year it was awarded
/// @param image        a picture, or null
/// @param citationHtml the citation as HTML, escaped by [PortableText] and safe to print as it is
public record LuddePrize(String recipient, int year, @Nullable Image image, String citationHtml) {
}
