package se.teaterihuskvarna.content;

import org.jspecify.annotations.Nullable;

/// A partner from Sanity's `partner` type, listed on `/partners`: R004.
///
/// @param name        the partner's name
/// @param logo        its logo, or null
/// @param url         its website, or null
/// @param description a sentence or two, or null
public record Partner(String name, @Nullable Image logo, @Nullable String url, @Nullable String description) {
}
