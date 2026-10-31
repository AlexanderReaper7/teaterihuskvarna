package se.teaterihuskvarna.content;

import org.jspecify.annotations.Nullable;

/// A member of the board, from Sanity's `styrelseledamot` type: R004, on the
/// page Styrelsen. The type has no contact fields, so none can reach the page.
///
/// @param name           the person's name
/// @param role           the role on the board, such as Ordförande
/// @param image          a photo, or null
/// @param responsibility a line on what the person does, or null
public record BoardMember(String name, String role, @Nullable Image image, @Nullable String responsibility) {
}
