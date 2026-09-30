package se.teaterihuskvarna.content;

import org.jspecify.annotations.Nullable;

/// A page of fixed information from Sanity's `sida` type, such as Styrelsen: R004.
///
/// @param slug     the page's slug in Sanity, and its address, `/<slug>`
/// @param title    its heading
/// @param image    the picture at the top, or null
/// @param bodyHtml the text as HTML, escaped by [PortableText] and safe to print as it is
public record Page(String slug, String title, @Nullable Image image, String bodyHtml) {
}
