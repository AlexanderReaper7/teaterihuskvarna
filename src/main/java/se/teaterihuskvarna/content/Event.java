package se.teaterihuskvarna.content;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// An event from Sanity's `evenemang` type.
///
/// @param id              the published document's id, which a volunteer shift refers to
/// @param slug            the event's address, `/evenemang/<slug>`
/// @param title           its name
/// @param series          the series it belongs to, or null for none
/// @param startsAt        when it starts
/// @param place           where, or null when the editor left it out
/// @param summary         a sentence or two for lists, or null
/// @param image           the main picture, or null
/// @param descriptionHtml the description as HTML, escaped by [PortableText] and safe to print as it is
/// @param gallery         more pictures, possibly none
/// @param ticketUrl       where to buy tickets, or null
public record Event(
        String id,
        String slug,
        String title,
        @Nullable Series series,
        Instant startsAt,
        @Nullable String place,
        @Nullable String summary,
        @Nullable Image image,
        String descriptionHtml,
        List<Image> gallery,
        @Nullable String ticketUrl) {

    /// Copies the gallery, so the event cannot change after it is made.
    public Event {
        gallery = List.copyOf(gallery);
    }
}
