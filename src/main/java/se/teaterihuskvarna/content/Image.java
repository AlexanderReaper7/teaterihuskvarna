package se.teaterihuskvarna.content;

/// A picture from Sanity's image CDN.
///
/// @param url    the picture, scaled to at most [Images#WIDTH] pixels wide
/// @param alt    the text in its place for someone who cannot see it, empty for a decoration
/// @param width  the scaled width in pixels, so the page keeps its space before the picture loads
/// @param height the scaled height in pixels
public record Image(String url, String alt, int width, int height) {
}
