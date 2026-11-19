package se.teaterihuskvarna.content;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// A news item from Sanity's `nyhet` type: R003.
///
/// @param id          the published document's id
/// @param slug        its address, `/nyheter/<slug>`
/// @param title       the headline
/// @param publishedAt the date it is shown with and sorted by
/// @param summary     the ingress, shown in the list, or null
/// @param image       the main picture, or null
/// @param bodyHtml    the text as HTML, escaped by [PortableText] and safe to print as it is
/// @param videoUrl    a YouTube or Vimeo address to link to, or null
public record NewsItem(
        String id,
        String slug,
        String title,
        Instant publishedAt,
        @Nullable String summary,
        @Nullable Image image,
        String bodyHtml,
        @Nullable String videoUrl) implements SanityDocument {

    @Override
    public String type() {
        return "nyhet";
    }
}
