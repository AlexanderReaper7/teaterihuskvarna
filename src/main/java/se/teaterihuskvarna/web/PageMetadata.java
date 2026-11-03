package se.teaterihuskvarna.web;

/// Search and sharing text for one public page. URLs use the configured site address.
///
/// @param title the page title
/// @param description plain text for search results and sharing
/// @param url the canonical address, without filters or tokens
/// @param image the page's picture, or the association logo
/// @param imageAlt the picture's alternative text
/// @param type `article` for news, `website` for other pages
public record PageMetadata(String title, String description, String url, String image, String imageAlt, String type) {
}
